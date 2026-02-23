package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.position.AccountType.*;
import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.POSITIONS;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.FUND_UNIT;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.ADJUSTMENT;
import static ee.tuleva.onboarding.ledger.SystemAccount.*;
import static ee.tuleva.onboarding.ledger.UserAccount.FUND_UNITS;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.fees.FeeAccrual;
import ee.tuleva.onboarding.investment.fees.FeeAccrualRepository;
import ee.tuleva.onboarding.investment.fees.FeeCalculationService;
import ee.tuleva.onboarding.investment.fees.FeeType;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionImportService;
import ee.tuleva.onboarding.investment.position.FundPositionLedgerService;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.investment.position.parser.SebFundPositionParser;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import ee.tuleva.onboarding.ledger.*;
import ee.tuleva.onboarding.user.User;
import jakarta.persistence.EntityManager;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@Transactional
class NavPipelineIntegrationTest {

  static final Path NAV_CSV_DIR = Path.of("src/test/resources/nav-test-data");

  @Autowired InvestmentReportService investmentReportService;
  @Autowired SebFundPositionParser sebFundPositionParser;
  @Autowired FundPositionImportService fundPositionImportService;
  @Autowired FundPositionRepository fundPositionRepository;
  @Autowired FundPositionLedgerService fundPositionLedgerService;
  @Autowired NavPositionLedger navPositionLedger;
  @Autowired FeeAccrualRepository feeAccrualRepository;
  @Autowired NavCalculationService navCalculationService;
  @Autowired NavPublisher navPublisher;
  @Autowired LedgerService ledgerService;
  @Autowired FeeCalculationService feeCalculationService;
  @Autowired JdbcClient jdbcClient;
  @Autowired EntityManager entityManager;

  User testUser = sampleUser().personalCode("38001010001").build();

  @ParameterizedTest
  @MethodSource("testPairs")
  @SneakyThrows
  void fullPipelineProducesExpectedNav(NavTestPair pair) {
    var navData = parseNavCsv(pair.navCsvFile);

    importPositionReport(pair.positionReportFile, navData.navDate);
    recordPositionsToLedger(navData);
    recordFeeAccruals(navData);
    insertPrices(pair.calculationDate);
    issueFundUnits(navData.unitsOutstanding, navData.navDate);
    entityManager.flush();
    entityManager.clear();

    var result = navCalculationService.calculate(TKF100, pair.calculationDate);
    navPublisher.publish(result);

    assertThat(result.cashPosition()).isEqualByComparingTo(navData.cashPosition);
    assertThat(result.receivables()).isEqualByComparingTo(navData.tradeReceivables);
    assertThat(result.payables()).isEqualByComparingTo(navData.tradePayables.negate());
    assertThat(result.managementFeeAccrual())
        .isEqualByComparingTo(navData.managementFeeAccrual.negate());
    assertThat(result.depotFeeAccrual()).isEqualByComparingTo(navData.depotFeeAccrual.negate());
    assertThat(result.unitsOutstanding().setScale(3, HALF_UP))
        .isEqualByComparingTo(navData.unitsOutstanding.setScale(3, HALF_UP));
    assertThat(result.pendingSubscriptions()).isEqualByComparingTo(ZERO);
    assertThat(result.pendingRedemptions()).isEqualByComparingTo(ZERO);
    assertThat(result.blackrockAdjustment()).isEqualByComparingTo(ZERO);
    assertThat(result.navPerUnit().setScale(4, HALF_UP))
        .isEqualByComparingTo(navData.expectedNavPerUnit.setScale(4, HALF_UP));
  }

  @Test
  void recordPositions_isIdempotent() {
    LocalDate date = LocalDate.of(2026, 2, 1);
    Map<String, BigDecimal> units = Map.of("IE00BFG1TM61", new BigDecimal("1000.00000"));

    navPositionLedger.recordPositions("TKF100", date, units, ZERO, ZERO, ZERO);
    entityManager.flush();
    navPositionLedger.recordPositions("TKF100", date, units, ZERO, ZERO, ZERO);
    entityManager.flush();

    int count =
        jdbcClient.sql("SELECT COUNT(*) FROM ledger.transaction").query(Integer.class).single();
    assertThat(count).isEqualTo(1);
  }

  @Test
  void feeCalculationOnlyAffectsNavEnabledFundInLedger() {
    LocalDate date = LocalDate.of(2025, 3, 15);
    BigDecimal tkf100Aum = new BigDecimal("50000000");

    insertPositionCalculation(TKF100, date, tkf100Aum);
    insertPositionCalculation(TUK75, date, new BigDecimal("1000000000"));
    insertPositionCalculation(TKF100, LocalDate.of(2025, 2, 28), new BigDecimal("48000000"));
    insertPositionCalculation(TUK75, LocalDate.of(2025, 2, 28), new BigDecimal("980000000"));

    fundPositionRepository.save(
        FundPosition.builder()
            .navDate(date)
            .fund(TKF100)
            .accountType(CASH)
            .accountName("Cash")
            .marketValue(tkf100Aum)
            .currency("EUR")
            .createdAt(Instant.now())
            .build());
    entityManager.flush();
    navPositionLedger.recordPositions(TKF100.name(), date, Map.of(), tkf100Aum, ZERO, ZERO);
    entityManager.flush();

    insertFeeRate(TKF100, "MANAGEMENT", new BigDecimal("0.0025"));
    insertFeeRate(TUK75, "MANAGEMENT", new BigDecimal("0.0025"));
    insertDepotFeeTier(new BigDecimal("0.00035"));

    feeCalculationService.calculateDailyFees(date);
    entityManager.flush();

    int ledgerTransactionCount =
        jdbcClient.sql("SELECT COUNT(*) FROM ledger.transaction").query(Integer.class).single();
    assertThat(ledgerTransactionCount).isEqualTo(3);

    BigDecimal managementFeeBalance = getSystemAccountBalance(MANAGEMENT_FEE_ACCRUAL);
    BigDecimal depotFeeBalance = getSystemAccountBalance(DEPOT_FEE_ACCRUAL);

    BigDecimal tkf100ManagementFee =
        jdbcClient
            .sql(
                """
                SELECT daily_amount_net FROM investment_fee_accrual
                WHERE fund_code = 'TKF100' AND fee_type = 'MANAGEMENT' AND accrual_date = :date
                """)
            .param("date", date)
            .query(BigDecimal.class)
            .single();

    assertThat(managementFeeBalance.abs().setScale(2, HALF_UP))
        .isEqualByComparingTo(tkf100ManagementFee.setScale(2, HALF_UP));
    assertThat(depotFeeBalance).isNotEqualByComparingTo(ZERO);
  }

  @Test
  void feeCalculationMatchesSebNavReports() {
    insertFeeRate(TKF100, "MANAGEMENT", new BigDecimal("0.0016"), LocalDate.of(2026, 2, 1));
    insertFeeRate(TKF100, "DEPOT", ZERO, LocalDate.of(2026, 2, 1));
    insertDepotFeeTier(new BigDecimal("0.00035"));
    insertPrices(LocalDate.of(2026, 3, 1));

    LocalDate feb2 = LocalDate.of(2026, 2, 2);
    fundPositionRepository.save(
        FundPosition.builder()
            .navDate(feb2)
            .fund(TKF100)
            .accountType(CASH)
            .accountName("Cash account in SEB Pank")
            .marketValue(new BigDecimal("5301827.32"))
            .currency("EUR")
            .createdAt(Instant.now())
            .build());
    entityManager.flush();
    fundPositionLedgerService.recordPositionsToLedger(TKF100, feb2);
    entityManager.flush();
    feeCalculationService.calculateDailyFeesForFund(TKF100, feb2);
    entityManager.flush();

    List<NavCsvData> allNavData = parseAllNavCsvsSorted();
    LocalDate previousNavDate = feb2;

    for (NavCsvData navData : allNavData) {
      LocalDate gapDay = previousNavDate.plusDays(1);
      while (gapDay.isBefore(navData.navDate)) {
        feeCalculationService.calculateDailyFeesForFund(TKF100, gapDay);
        gapDay = gapDay.plusDays(1);
      }

      navData.positions.forEach(fundPositionRepository::save);
      entityManager.flush();
      fundPositionLedgerService.recordPositionsToLedger(TKF100, navData.navDate);
      entityManager.flush();
      insertPositionCalculation(TKF100, navData.navDate, navData.securitiesTotal);

      feeCalculationService.calculateDailyFeesForFund(TKF100, navData.navDate);
      entityManager.flush();

      previousNavDate = navData.navDate;
    }

    BigDecimal cumulativeFee = getCumulativeManagementFee();
    assertThat(cumulativeFee.setScale(2, HALF_UP))
        .as("Cumulative management fee through %s", allNavData.getLast().navDate)
        .isEqualByComparingTo(new BigDecimal("511.84"));
  }

  private void insertPositionCalculation(TulevaFund fund, LocalDate date, BigDecimal marketValue) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_position_calculation
            (isin, fund_code, date, quantity, calculated_market_value, validation_status, created_at)
            VALUES (:isin, :fundCode, :date, 1, :marketValue, 'OK', now())
            """)
        .param("isin", "TEST_ISIN_" + fund.name())
        .param("fundCode", fund.name())
        .param("date", date)
        .param("marketValue", marketValue)
        .update();
  }

  @SneakyThrows
  private List<NavCsvData> parseAllNavCsvsSorted() {
    return Files.list(NAV_CSV_DIR)
        .filter(path -> path.getFileName().toString().startsWith("TKF NAV"))
        .sorted()
        .map(this::parseNavCsv)
        .sorted(Comparator.comparing(data -> data.navDate))
        .toList();
  }

  private BigDecimal getCumulativeManagementFee() {
    return jdbcClient
        .sql(
            """
            SELECT COALESCE(SUM(daily_amount_net), 0)
            FROM investment_fee_accrual
            WHERE fund_code = 'TKF100' AND fee_type = 'MANAGEMENT'
            """)
        .query(BigDecimal.class)
        .single();
  }

  private void insertFeeRate(
      TulevaFund fund, String feeType, BigDecimal annualRate, LocalDate validFrom) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_fee_rate (fund_code, fee_type, annual_rate, valid_from, created_by)
            VALUES (:fundCode, :feeType, :annualRate, :validFrom, 'TEST')
            """)
        .param("fundCode", fund.name())
        .param("feeType", feeType)
        .param("annualRate", annualRate)
        .param("validFrom", validFrom)
        .update();
  }

  private void insertFeeRate(TulevaFund fund, String feeType, BigDecimal annualRate) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_fee_rate (fund_code, fee_type, annual_rate, valid_from, created_by)
            VALUES (:fundCode, :feeType, :annualRate, :validFrom, 'TEST')
            """)
        .param("fundCode", fund.name())
        .param("feeType", feeType)
        .param("annualRate", annualRate)
        .param("validFrom", LocalDate.of(2025, 1, 1))
        .update();
  }

  private void insertDepotFeeTier(BigDecimal annualRate) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_depot_fee_tier (min_aum, annual_rate, valid_from)
            VALUES (:minAum, :annualRate, :validFrom)
            """)
        .param("minAum", 0)
        .param("annualRate", annualRate)
        .param("validFrom", LocalDate.of(2025, 1, 1))
        .update();
  }

  private BigDecimal getSystemAccountBalance(SystemAccount systemAccount) {
    return jdbcClient
        .sql(
            """
            SELECT COALESCE(SUM(e.amount), 0)
            FROM ledger.entry e
            JOIN ledger.account a ON e.account_id = a.id
            WHERE a.name = :accountName
            """)
        .param("accountName", systemAccount.getAccountName())
        .query(BigDecimal.class)
        .single();
  }

  @SneakyThrows
  static Stream<NavTestPair> testPairs() {
    return Files.list(NAV_CSV_DIR)
        .filter(path -> path.getFileName().toString().startsWith("TKF NAV"))
        .sorted()
        .map(NavPipelineIntegrationTest::toTestPair)
        .flatMap(Optional::stream);
  }

  private static Optional<NavTestPair> toTestPair(Path navCsvFile) {
    LocalDate navDate = parseNavDate(navCsvFile);
    LocalDate calculationDate = parseCalculationDateFromFilename(navCsvFile);
    Path positionReport = NAV_CSV_DIR.resolve(navDate + "_positions.csv");
    if (Files.exists(positionReport)) {
      return Optional.of(new NavTestPair(navCsvFile, positionReport, navDate, calculationDate));
    }
    return Optional.empty();
  }

  private static LocalDate parseCalculationDateFromFilename(Path csvFile) {
    String filename = csvFile.getFileName().toString();
    String datePart = filename.replaceAll("\\D", "");
    return LocalDate.parse(datePart, DateTimeFormatter.ofPattern("ddMMyyyy"));
  }

  @SneakyThrows
  private static LocalDate parseNavDate(Path navCsvFile) {
    List<String> lines = Files.readAllLines(navCsvFile);
    String[] fields = lines.get(1).split(",");
    return LocalDate.parse(fields[0]);
  }

  @SneakyThrows
  private void importPositionReport(Path positionReportFile, LocalDate reportDate) {
    byte[] csvBytes = Files.readAllBytes(positionReportFile);
    var report =
        investmentReportService.saveReport(
            SEB, POSITIONS, reportDate, new ByteArrayInputStream(csvBytes), ';', 5, Map.of());
    var positions =
        sebFundPositionParser.parse(report.getRawData(), reportDate, report.getMetadata());
    fundPositionImportService.importPositions(positions);
  }

  private void recordPositionsToLedger(NavCsvData navData) {
    var securityPositions =
        fundPositionRepository.findByNavDateAndFundAndAccountType(
            navData.navDate, TKF100, SECURITY);
    Map<String, BigDecimal> securitiesUnits =
        securityPositions.stream()
            .filter(position -> position.getAccountId() != null)
            .collect(toMap(FundPosition::getAccountId, FundPosition::getQuantity));

    navPositionLedger.recordPositions(
        TKF100.name(),
        navData.navDate,
        securitiesUnits,
        navData.cashPosition,
        navData.tradeReceivables,
        navData.tradePayables);
  }

  private void recordFeeAccruals(NavCsvData navData) {
    if (navData.managementFeeAccrual.signum() != 0) {
      feeAccrualRepository.save(
          buildTestFeeAccrual(
              navData.navDate, FeeType.MANAGEMENT, navData.managementFeeAccrual.negate()));
    }
    if (navData.depotFeeAccrual.signum() != 0) {
      feeAccrualRepository.save(
          buildTestFeeAccrual(navData.navDate, FeeType.DEPOT, navData.depotFeeAccrual.negate()));
    }
  }

  private FeeAccrual buildTestFeeAccrual(LocalDate date, FeeType feeType, BigDecimal amount) {
    return FeeAccrual.builder()
        .fund(TKF100)
        .feeType(feeType)
        .accrualDate(date)
        .feeMonth(date.withDayOfMonth(1))
        .baseValue(ZERO)
        .annualRate(ZERO)
        .dailyAmountNet(amount)
        .dailyAmountGross(amount)
        .daysInYear(date.lengthOfYear())
        .build();
  }

  @SneakyThrows
  private void insertPrices(LocalDate calculationDate) {
    var priceLines = Files.readAllLines(Path.of("src/test/resources/nav-test-data/prices.csv"));
    for (int i = 1; i < priceLines.size(); i++) {
      String[] parts = priceLines.get(i).split(",");
      LocalDate priceDate = LocalDate.parse(parts[1]);
      if (priceDate.isBefore(calculationDate)) {
        entityManager
            .createNativeQuery(
                "INSERT INTO index_values (key, date, value, provider, updated_at) VALUES (:key, :date, :value, 'EODHD', CURRENT_TIMESTAMP)")
            .setParameter("key", parts[0])
            .setParameter("date", priceDate)
            .setParameter("value", new BigDecimal(parts[2]))
            .executeUpdate();
      }
    }
  }

  private void issueFundUnits(BigDecimal unitsOutstanding, LocalDate navDate) {
    LedgerAccount fundUnitsOutstandingAccount =
        ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING);
    LedgerAccount userFundUnitsAccount = ledgerService.getUserAccount(testUser, FUND_UNITS);

    BigDecimal units = unitsOutstanding.setScale(5, HALF_UP);

    Instant transactionDate = navDate.atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant();
    var transaction =
        LedgerTransaction.builder()
            .transactionType(ADJUSTMENT)
            .transactionDate(transactionDate)
            .build();

    var systemEntry =
        LedgerEntry.builder()
            .amount(units)
            .assetType(FUND_UNIT)
            .account(fundUnitsOutstandingAccount)
            .transaction(transaction)
            .build();

    var userEntry =
        LedgerEntry.builder()
            .amount(units.negate())
            .assetType(FUND_UNIT)
            .account(userFundUnitsAccount)
            .transaction(transaction)
            .build();

    transaction.getEntries().add(systemEntry);
    transaction.getEntries().add(userEntry);
    entityManager.persist(transaction);
  }

  @SneakyThrows
  private NavCsvData parseNavCsv(Path csvFile) {
    List<String> lines = Files.readAllLines(csvFile);
    var data = new NavCsvData();

    for (int i = 1; i < lines.size(); i++) {
      String[] fields = lines.get(i).split(",");
      String accountType = fields[2];
      String accountName = fields[3];
      BigDecimal quantity = new BigDecimal(fields[5]);
      BigDecimal marketPrice = new BigDecimal(fields[6]);
      BigDecimal marketValue = new BigDecimal(fields[8]);

      if (data.navDate == null) {
        data.navDate = LocalDate.parse(fields[0]);
      }

      String accountId = fields[4];
      String currency = fields[7];

      switch (accountType) {
        case "CASH" -> {
          data.cashPosition = marketValue;
          data.positions.add(
              FundPosition.builder()
                  .navDate(data.navDate)
                  .fund(TKF100)
                  .accountType(CASH)
                  .accountName(accountName)
                  .quantity(quantity)
                  .marketPrice(marketPrice)
                  .currency(currency)
                  .marketValue(marketValue)
                  .createdAt(Instant.now())
                  .build());
        }
        case "SECURITY" -> {
          data.securitiesTotal = data.securitiesTotal.add(marketValue);
          data.positions.add(
              FundPosition.builder()
                  .navDate(data.navDate)
                  .fund(TKF100)
                  .accountType(SECURITY)
                  .accountName(accountName)
                  .accountId(accountId.isEmpty() ? null : accountId)
                  .quantity(quantity)
                  .marketPrice(marketPrice)
                  .currency(currency)
                  .marketValue(marketValue)
                  .createdAt(Instant.now())
                  .build());
        }
        case "RECEIVABLES" -> {
          if (accountName.startsWith("Total receivables")) {
            data.tradeReceivables = marketValue;
          }
          data.positions.add(
              FundPosition.builder()
                  .navDate(data.navDate)
                  .fund(TKF100)
                  .accountType(RECEIVABLES)
                  .accountName(accountName)
                  .quantity(quantity)
                  .marketPrice(marketPrice)
                  .currency(currency)
                  .marketValue(marketValue)
                  .createdAt(Instant.now())
                  .build());
        }
        case "LIABILITY" -> {
          if (accountName.startsWith("Total payables")) {
            data.tradePayables = marketValue;
          }
          data.positions.add(
              FundPosition.builder()
                  .navDate(data.navDate)
                  .fund(TKF100)
                  .accountType(LIABILITY)
                  .accountName(accountName)
                  .quantity(quantity)
                  .marketPrice(marketPrice)
                  .currency(currency)
                  .marketValue(marketValue)
                  .createdAt(Instant.now())
                  .build());
        }
        case "LIABILITY_FEE" -> {
          if (accountName.startsWith("Management fee")) {
            data.managementFeeAccrual = marketValue;
          } else if (accountName.startsWith("Custody fee")) {
            data.depotFeeAccrual = marketValue;
          }
        }
        case "UNITS" -> {
          data.unitsOutstanding = quantity;
          data.expectedNavPerUnit = marketPrice;
        }
      }
    }

    return data;
  }

  record NavTestPair(
      Path navCsvFile, Path positionReportFile, LocalDate navDate, LocalDate calculationDate) {
    @Override
    public String toString() {
      return navCsvFile.getFileName().toString();
    }
  }

  private static class NavCsvData {
    LocalDate navDate;
    BigDecimal cashPosition = ZERO;
    BigDecimal tradeReceivables = ZERO;
    BigDecimal tradePayables = ZERO;
    BigDecimal managementFeeAccrual = ZERO;
    BigDecimal depotFeeAccrual = ZERO;
    BigDecimal unitsOutstanding = ZERO;
    BigDecimal expectedNavPerUnit = ZERO;
    BigDecimal securitiesTotal = ZERO;
    final List<FundPosition> positions = new ArrayList<>();
  }
}
