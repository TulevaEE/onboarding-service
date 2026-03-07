package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.position.AccountType.NAV;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.FUND_UNIT;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.ADJUSTMENT;
import static ee.tuleva.onboarding.ledger.SystemAccount.*;
import static ee.tuleva.onboarding.ledger.UserAccount.FUND_UNITS;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.ledger.*;
import ee.tuleva.onboarding.ledger.LedgerAccount.AssetType;
import ee.tuleva.onboarding.user.User;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@Transactional
class NavCalculationIntegrationTest {

  @Autowired NavCalculationService navCalculationService;
  @Autowired LedgerService ledgerService;
  @Autowired FundPositionRepository fundPositionRepository;
  @Autowired EntityManager entityManager;
  @Autowired JdbcClient jdbcClient;

  User testUser = sampleUser().personalCode("38001010001").build();

  @BeforeEach
  void setUp() {
    insertFeeRate(TKF100, "MANAGEMENT", new BigDecimal("0.0029"));
    insertFeeRate(TKF100, "DEPOT", new BigDecimal("0.00035"));
    insertFeeRate(TUK75, "MANAGEMENT", new BigDecimal("0.0029"));
    insertFeeRate(TUK75, "DEPOT", new BigDecimal("0.00035"));
  }

  @ParameterizedTest
  @MethodSource("navCsvFiles")
  @SneakyThrows
  void calculatedNavMatchesManualCalculation(Path csvFile) {
    var csvData = parseCsv(csvFile);
    var calculationDate = parseCalculationDateFromFilename(csvFile);

    setupLedgerBalances(csvData, calculationDate);
    setupFundPosition(csvData.navDate);
    entityManager.flush();
    entityManager.clear();

    var result = navCalculationService.calculate(TKF100, calculationDate);

    assertThat(result.cashPosition()).isEqualByComparingTo(csvData.cashPosition);
    assertThat(result.receivables()).isEqualByComparingTo(csvData.tradeReceivables);
    assertThat(result.payables()).isEqualByComparingTo(csvData.tradePayables.negate());
    assertThat(result.managementFeeAccrual()).isPositive();
    assertThat(result.depotFeeAccrual()).isPositive();
    assertThat(result.unitsOutstanding().setScale(3, HALF_UP))
        .isEqualByComparingTo(csvData.unitsOutstanding.setScale(3, HALF_UP));
    assertThat(result.pendingSubscriptions()).isEqualByComparingTo(ZERO);
    assertThat(result.pendingRedemptions()).isEqualByComparingTo(ZERO);
    assertThat(result.blackrockAdjustment()).isEqualByComparingTo(ZERO);
    assertThat(result.navPerUnit()).isPositive();
  }

  @Test
  @SneakyThrows
  void retroactiveNavCalculation_excludesFutureLedgerEntries() {
    var csvFile = Path.of("src/test/resources/nav-test-data/TKF NAV Arvutamine 04022026.csv");
    var csvData = parseCsv(csvFile);
    var calculationDate = LocalDate.of(2026, 2, 4);

    setupLedgerBalances(csvData, calculationDate);
    setupFundPosition(csvData.navDate);

    Instant futureTransactionDate =
        LocalDate.of(2026, 2, 5).atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant();
    createSystemAccountBalance(CASH_POSITION, new BigDecimal("999999"), EUR, futureTransactionDate);
    createSecuritiesUnitBalance(
        TKF100, "IE00BMDBMY19", new BigDecimal("50000.00000"), futureTransactionDate);
    setupFundPosition(LocalDate.of(2026, 2, 5));

    entityManager.flush();
    entityManager.clear();

    var result = navCalculationService.calculate(TKF100, calculationDate);

    assertThat(result.cashPosition()).isEqualByComparingTo(csvData.cashPosition);
    assertThat(result.positionReportDate()).isEqualTo(csvData.navDate);
    assertThat(result.navPerUnit()).isPositive();
  }

  @Test
  void navCalculation_usesPriceCutoffToFilterOutLatePublishedPrices() {
    LocalDate calculationDate = LocalDate.of(2026, 3, 4);
    LocalDate priceDate = LocalDate.of(2026, 3, 3);
    Instant transactionDate = priceDate.atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant();

    // TUK75 cutoff is 11:00 EET = 09:00 UTC on calculation date
    // EUFUND price published Mar 3 21:00 UTC (before cutoff) → should be used
    // BLACKROCK price published Mar 4 15:00 UTC (after cutoff) → should be filtered out
    insertPriceWithTimestamp(
        "IE0009FT4LX4.EUFUND",
        priceDate,
        new BigDecimal("15.529"),
        "EODHD",
        Instant.parse("2026-03-03T21:00:00Z"));
    insertPriceWithTimestamp(
        "IE0009FT4LX4.BLACKROCK",
        priceDate,
        new BigDecimal("15.415"),
        "BLACKROCK",
        Instant.parse("2026-03-04T15:00:00Z"));

    createSecuritiesUnitBalance(
        TUK75, "IE0009FT4LX4", new BigDecimal("100000.00000"), transactionDate);
    createSystemAccountBalance(
        TUK75, CASH_POSITION, new BigDecimal("50000.00"), EUR, transactionDate);
    createSystemAccountBalance(TUK75, TRADE_RECEIVABLES, ZERO, EUR, transactionDate);
    createSystemAccountBalance(TUK75, TRADE_PAYABLES, ZERO, EUR, transactionDate);
    createSystemAccountBalance(TUK75, BLACKROCK_ADJUSTMENT, ZERO, EUR, transactionDate);
    createFundUnitsOutstandingBalance(TUK75, new BigDecimal("100000.00000"), priceDate);
    setupFundPosition(TUK75, priceDate);

    entityManager.flush();
    entityManager.clear();

    var result = navCalculationService.calculate(TUK75, calculationDate);

    // Should use EUFUND price (15.529), not BLACKROCK (15.415)
    assertThat(result.securitiesValue()).isEqualByComparingTo("1552900.00");
  }

  @Test
  void navCalculation_feeAccrualVisibleBeforeFundCutoff() {
    LocalDate calculationDate = LocalDate.of(2026, 3, 4);
    LocalDate priceDate = LocalDate.of(2026, 3, 3);
    Instant transactionDate = priceDate.atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant();

    insertPriceWithTimestamp(
        "IE0009FT4LX4.EUFUND",
        priceDate,
        new BigDecimal("15.529"),
        "EODHD",
        Instant.parse("2026-03-03T21:00:00Z"));

    createSecuritiesUnitBalance(
        TUK75, "IE0009FT4LX4", new BigDecimal("100000.00000"), transactionDate);
    createSystemAccountBalance(
        TUK75, CASH_POSITION, new BigDecimal("50000.00"), EUR, transactionDate);
    createSystemAccountBalance(TUK75, TRADE_RECEIVABLES, ZERO, EUR, transactionDate);
    createSystemAccountBalance(TUK75, TRADE_PAYABLES, ZERO, EUR, transactionDate);
    createSystemAccountBalance(TUK75, BLACKROCK_ADJUSTMENT, ZERO, EUR, transactionDate);
    createFundUnitsOutstandingBalance(TUK75, new BigDecimal("100000.00000"), priceDate);
    setupFundPosition(TUK75, priceDate);

    entityManager.flush();
    entityManager.clear();

    var result = navCalculationService.calculate(TUK75, calculationDate);

    // Fees computed inline from base value (securitiesValue + cash)
    assertThat(result.managementFeeAccrual()).isPositive();
    assertThat(result.depotFeeAccrual()).isPositive();
  }

  @SneakyThrows
  static Stream<Path> navCsvFiles() {
    var directory = Path.of("src/test/resources/nav-test-data");
    return Files.list(directory)
        .filter(path -> path.getFileName().toString().startsWith("TKF NAV"))
        .sorted();
  }

  private void setupLedgerBalances(CsvData csvData, LocalDate calculationDate) {
    Instant transactionDate = csvData.navDate.atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant();
    createSystemAccountBalance(CASH_POSITION, csvData.cashPosition, EUR, transactionDate);
    createSecuritiesUnitBalances(csvData, transactionDate);
    insertPrices();
    createSystemAccountBalance(TRADE_RECEIVABLES, csvData.tradeReceivables, EUR, transactionDate);
    createSystemAccountBalance(TRADE_PAYABLES, csvData.tradePayables, EUR, transactionDate);
    createSystemAccountBalance(BLACKROCK_ADJUSTMENT, ZERO, EUR, transactionDate);
    createFundUnitsOutstandingBalance(csvData.unitsOutstanding, csvData.navDate);
  }

  private void createSystemAccountBalance(
      SystemAccount systemAccount,
      BigDecimal amount,
      AssetType assetType,
      Instant transactionDate) {
    createSystemAccountBalance(TKF100, systemAccount, amount, assetType, transactionDate);
  }

  private void createSecuritiesUnitBalances(CsvData csvData, Instant transactionDate) {
    csvData.securitiesUnitsByIsin.forEach(
        (isin, quantity) ->
            createSecuritiesUnitBalance(
                TKF100, isin, quantity.setScale(5, HALF_UP), transactionDate));
  }

  @SneakyThrows
  private void insertPrices() {
    var priceLines = Files.readAllLines(Path.of("src/test/resources/nav-test-data/prices.csv"));
    for (int i = 1; i < priceLines.size(); i++) {
      String[] parts = priceLines.get(i).split(",");
      LocalDate priceDate = LocalDate.parse(parts[1]);
      Instant updatedAt = priceDate.atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant();
      entityManager
          .createNativeQuery(
              "INSERT INTO index_values (key, date, value, provider, updated_at) VALUES (:key, :date, :value, 'EODHD', :updatedAt)")
          .setParameter("key", parts[0])
          .setParameter("date", priceDate)
          .setParameter("value", new BigDecimal(parts[2]))
          .setParameter("updatedAt", java.sql.Timestamp.from(updatedAt))
          .executeUpdate();
    }
  }

  private void createFundUnitsOutstandingBalance(BigDecimal unitsOutstanding, LocalDate navDate) {
    createFundUnitsOutstandingBalance(TKF100, unitsOutstanding, navDate);
  }

  private void setupFundPosition(LocalDate navDate) {
    setupFundPosition(TKF100, navDate);
  }

  private LocalDate parseCalculationDateFromFilename(Path csvFile) {
    String filename = csvFile.getFileName().toString();
    String datePart = filename.replaceAll("\\D", "");
    var formatter = DateTimeFormatter.ofPattern("ddMMyyyy");
    return LocalDate.parse(datePart, formatter);
  }

  @SneakyThrows
  private CsvData parseCsv(Path csvFile) {
    List<String> lines = Files.readAllLines(csvFile);
    var data = new CsvData();

    BigDecimal securitiesSum = ZERO;

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

      switch (accountType) {
        case "CASH" -> data.cashPosition = marketValue;
        case "SECURITY" -> {
          securitiesSum = securitiesSum.add(marketValue);
          String isin = fields[4];
          if (!isin.isEmpty() && quantity.signum() != 0) {
            data.securitiesUnitsByIsin.put(isin, quantity);
          }
        }
        case "RECEIVABLES" -> {
          if (accountName.startsWith("Total receivables")) {
            data.tradeReceivables = marketValue;
          }
        }
        case "LIABILITY" -> {
          if (accountName.startsWith("Total payables")) {
            data.tradePayables = marketValue;
          }
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

    data.securitiesValue = securitiesSum;
    return data;
  }

  private void insertPriceWithTimestamp(
      String key, LocalDate date, BigDecimal value, String provider, Instant updatedAt) {
    entityManager
        .createNativeQuery(
            "INSERT INTO index_values (key, date, value, provider, updated_at) VALUES (:key, :date, :value, :provider, :updatedAt)")
        .setParameter("key", key)
        .setParameter("date", date)
        .setParameter("value", value)
        .setParameter("provider", provider)
        .setParameter("updatedAt", java.sql.Timestamp.from(updatedAt))
        .executeUpdate();
  }

  private void createSecuritiesUnitBalance(
      TulevaFund fund, String isin, BigDecimal units, Instant transactionDate) {
    var securitiesUnitsAccount =
        LedgerAccount.builder()
            .name(SECURITIES_UNITS.getAccountName(fund, isin))
            .purpose(LedgerAccount.AccountPurpose.SYSTEM_ACCOUNT)
            .accountType(SECURITIES_UNITS.getAccountType())
            .assetType(SECURITIES_UNITS.getAssetType())
            .build();
    entityManager.persist(securitiesUnitsAccount);

    var securitiesUnitsEquityAccount =
        LedgerAccount.builder()
            .name(SECURITIES_UNITS_EQUITY.getAccountName(fund, isin))
            .purpose(LedgerAccount.AccountPurpose.SYSTEM_ACCOUNT)
            .accountType(SECURITIES_UNITS_EQUITY.getAccountType())
            .assetType(SECURITIES_UNITS_EQUITY.getAssetType())
            .build();
    entityManager.persist(securitiesUnitsEquityAccount);

    var transaction =
        LedgerTransaction.builder()
            .transactionType(ADJUSTMENT)
            .transactionDate(transactionDate)
            .build();

    var entry =
        LedgerEntry.builder()
            .amount(units)
            .assetType(FUND_UNIT)
            .account(securitiesUnitsAccount)
            .transaction(transaction)
            .build();

    var counterEntry =
        LedgerEntry.builder()
            .amount(units.negate())
            .assetType(FUND_UNIT)
            .account(securitiesUnitsEquityAccount)
            .transaction(transaction)
            .build();

    transaction.getEntries().add(entry);
    transaction.getEntries().add(counterEntry);
    entityManager.persist(transaction);
  }

  private void createSystemAccountBalance(
      TulevaFund fund,
      SystemAccount systemAccount,
      BigDecimal amount,
      AssetType assetType,
      Instant transactionDate) {
    if (amount.compareTo(ZERO) == 0) {
      ledgerService.getSystemAccount(systemAccount, fund);
      ledgerService.getSystemAccount(NAV_EQUITY, fund);
      return;
    }

    LedgerAccount account = ledgerService.getSystemAccount(systemAccount, fund);
    LedgerAccount navEquity = ledgerService.getSystemAccount(NAV_EQUITY, fund);

    var transaction =
        LedgerTransaction.builder()
            .transactionType(ADJUSTMENT)
            .transactionDate(transactionDate)
            .build();

    var entry =
        LedgerEntry.builder()
            .amount(amount)
            .assetType(assetType)
            .account(account)
            .transaction(transaction)
            .build();

    var counterEntry =
        LedgerEntry.builder()
            .amount(amount.negate())
            .assetType(assetType)
            .account(navEquity)
            .transaction(transaction)
            .build();

    transaction.getEntries().add(entry);
    transaction.getEntries().add(counterEntry);
    entityManager.persist(transaction);
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

  private void createFundUnitsOutstandingBalance(
      TulevaFund fund, BigDecimal unitsOutstanding, LocalDate navDate) {
    LedgerAccount fundUnitsOutstandingAccount =
        ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING, fund);
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

  private void setupFundPosition(TulevaFund fund, LocalDate navDate) {
    var position =
        FundPosition.builder()
            .navDate(navDate)
            .fund(fund)
            .accountType(NAV)
            .accountName("Net Asset Value")
            .accountId(fund.getIsin())
            .quantity(ONE)
            .marketPrice(ONE)
            .currency("EUR")
            .marketValue(ONE)
            .build();
    fundPositionRepository.save(position);
  }

  private static class CsvData {
    LocalDate navDate;
    BigDecimal cashPosition = ZERO;
    BigDecimal securitiesValue = ZERO;
    Map<String, BigDecimal> securitiesUnitsByIsin = new HashMap<>();
    BigDecimal tradeReceivables = ZERO;
    BigDecimal tradePayables = ZERO;
    BigDecimal managementFeeAccrual = ZERO;
    BigDecimal depotFeeAccrual = ZERO;
    BigDecimal unitsOutstanding = ZERO;
    BigDecimal expectedNavPerUnit = ZERO;
  }
}
