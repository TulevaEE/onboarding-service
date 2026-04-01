package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.position.AccountType.*;
import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.POSITIONS;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.FUND_UNIT;
import static ee.tuleva.onboarding.ledger.LedgerParty.PartyType.PERSON;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.ADJUSTMENT;
import static ee.tuleva.onboarding.ledger.SystemAccount.*;
import static ee.tuleva.onboarding.ledger.UserAccount.FUND_UNITS;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.fees.FeeCalculationService;
import ee.tuleva.onboarding.investment.fees.FeeResult;
import ee.tuleva.onboarding.investment.position.AccountType;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionImportService;
import ee.tuleva.onboarding.investment.position.FundPositionLedgerService;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.investment.position.parser.SebFundPositionParser;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import ee.tuleva.onboarding.ledger.*;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.user.User;
import jakarta.persistence.EntityManager;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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

@SpringBootTest
@Transactional
class NavPipelineIntegrationTest {

  static final Path NAV_CSV_DIR = Path.of("src/test/resources/nav-test-data");

  @Autowired InvestmentReportService investmentReportService;
  @Autowired SebFundPositionParser sebFundPositionParser;
  @Autowired FundPositionImportService fundPositionImportService;
  @Autowired FundPositionRepository fundPositionRepository;
  @Autowired NavPositionLedger navPositionLedger;
  @Autowired FundPositionLedgerService fundPositionLedgerService;
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

    Instant ledgerTime = navData.navDate.atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant();
    ClockHolder.setClock(Clock.fixed(ledgerTime, ZoneId.of("UTC")));
    try {
      importPositionReport(pair.positionReportFile, navData.navDate);
      recordPositionsToLedger(navData);
      insertFeeRate(TKF100, "MANAGEMENT", new BigDecimal("0.0029"), navData.navDate);
      insertFeeRate(TKF100, "DEPOT", new BigDecimal("0.00035"), navData.navDate);
      insertPrices(pair.calculationDate);
      issueFundUnits(navData.unitsOutstanding, navData.navDate);
      entityManager.flush();
      entityManager.clear();

      var result = navCalculationService.calculate(TKF100, pair.calculationDate);
      navPublisher.publish(result);

      assertThat(result.cashPosition()).isEqualByComparingTo(navData.cashPosition);
      assertThat(result.receivables()).isEqualByComparingTo(navData.tradeReceivables);
      assertThat(result.payables()).isEqualByComparingTo(navData.tradePayables.negate());
      assertThat(result.managementFeeAccrual()).isPositive();
      assertThat(result.depotFeeAccrual()).isPositive();
      assertThat(result.unitsOutstanding().setScale(3, HALF_UP))
          .isEqualByComparingTo(navData.unitsOutstanding.setScale(3, HALF_UP));
      assertThat(result.pendingSubscriptions()).isEqualByComparingTo(ZERO);
      assertThat(result.pendingRedemptions()).isEqualByComparingTo(ZERO);
      assertThat(result.blackrockAdjustment()).isEqualByComparingTo(ZERO);
      assertThat(result.navPerUnit()).isPositive();
    } finally {
      ClockHolder.setDefaultClock();
    }
  }

  @Test
  void retroactiveNavCalculation_usesPositionDataFromRecordedDate() {
    LocalDate feb3 = LocalDate.of(2026, 2, 3);
    LocalDate feb5 = LocalDate.of(2026, 2, 5);
    LocalDate calculationDate = LocalDate.of(2026, 2, 4);
    BigDecimal feb3Cash = new BigDecimal("5792137.97");
    BigDecimal feb5CashDelta = new BigDecimal("100000.00");

    navPositionLedger.recordPositions(TKF100, feb3, Map.of(), feb3Cash, ZERO, ZERO);
    entityManager.flush();

    navPositionLedger.recordPositions(TKF100, feb5, Map.of(), feb5CashDelta, ZERO, ZERO);
    entityManager.flush();

    fundPositionRepository.save(
        FundPosition.builder()
            .navDate(feb3)
            .fund(TKF100)
            .accountType(CASH)
            .accountName("Cash")
            .marketValue(feb3Cash)
            .currency("EUR")
            .createdAt(Instant.now())
            .build());

    issueFundUnits(new BigDecimal("1000000.000"), feb3);
    insertPrices(calculationDate);
    insertFeeRate(TKF100, "MANAGEMENT", new BigDecimal("0.0029"), LocalDate.of(2026, 1, 1));
    insertFeeRate(TKF100, "DEPOT", new BigDecimal("0.00035"), LocalDate.of(2026, 1, 1));
    entityManager.flush();
    entityManager.clear();

    var result = navCalculationService.calculate(TKF100, calculationDate);

    assertThat(result.cashPosition()).isEqualByComparingTo(feb3Cash);
  }

  @Test
  void recordPositions_isIdempotent() {
    LocalDate date = LocalDate.of(2026, 2, 1);
    Map<String, BigDecimal> units = Map.of("IE00BFG1TM61", new BigDecimal("1000.00000"));

    navPositionLedger.recordPositions(TKF100, date, units, ZERO, ZERO, ZERO);
    entityManager.flush();
    navPositionLedger.recordPositions(TKF100, date, units, ZERO, ZERO, ZERO);
    entityManager.flush();

    int count =
        jdbcClient.sql("SELECT COUNT(*) FROM ledger.transaction").query(Integer.class).single();
    assertThat(count).isEqualTo(1);
  }

  @Test
  void monthEndFeeSettlement_settlesFeesWhenMonthChanges() {
    BigDecimal aum = new BigDecimal("50000000");
    ZoneId eet = ZoneId.of("Europe/Tallinn");

    insertFeeRate(TKF100, "MANAGEMENT", new BigDecimal("0.0029"), LocalDate.of(2026, 1, 1));
    insertFeeRate(TKF100, "DEPOT", new BigDecimal("0.00035"), LocalDate.of(2026, 1, 1));

    // Establish first accrual at Feb 25
    Instant feb26Cutoff = LocalDate.of(2026, 2, 26).atStartOfDay(eet).toInstant();
    feeCalculationService.calculateFeesForNav(
        TKF100, LocalDate.of(2026, 2, 25), aum, feb26Cutoff, null);

    // Monday: fees for Feb 26-27 added, feeCutoff=Feb 28 00:00 EET
    // Accumulated balance visible: Feb 25, 26, 27 = 3 days
    Instant mondayCutoff = LocalDate.of(2026, 2, 28).atStartOfDay(eet).toInstant();
    FeeResult mondayResult =
        feeCalculationService.calculateFeesForNav(
            TKF100, LocalDate.of(2026, 2, 27), aum, mondayCutoff, null);

    // Tuesday: fees for Feb 28, Mar 1, 2 added
    // Feb settlement triggered when recording Mar 1
    // feeCutoff=Mar 3 00:00 → Feb fees settled, only Mar 1-2 visible
    Instant tuesdayCutoff = LocalDate.of(2026, 3, 3).atStartOfDay(eet).toInstant();
    FeeResult tuesdayResult =
        feeCalculationService.calculateFeesForNav(
            TKF100, LocalDate.of(2026, 3, 2), aum, tuesdayCutoff, null);

    // Daily management: 50,000,000 × 0.0029 / 365 → ledger 397.26/day
    // Daily depot: 50,000,000 × 0.00035 / 365 → ledger 47.95/day
    assertThat(mondayResult.managementFeeAccrual())
        .as("Monday: 3 days of management fees (Feb 25-27)")
        .isEqualByComparingTo(new BigDecimal("1191.78"));
    assertThat(mondayResult.depotFeeAccrual())
        .as("Monday: 3 days of depot fees (Feb 25-27)")
        .isEqualByComparingTo(new BigDecimal("143.84"));

    assertThat(tuesdayResult.managementFeeAccrual())
        .as("Tuesday: 2 days of management fees (Mar 1-2), Feb settled")
        .isEqualByComparingTo(new BigDecimal("794.52"));
    assertThat(tuesdayResult.depotFeeAccrual())
        .as("Tuesday: 2 days of depot fees (Mar 1-2), Feb settled")
        .isEqualByComparingTo(new BigDecimal("95.89"));
  }

  @Test
  void computeFeeBaseValue_inceptionDayPositionIsVisible() {
    LocalDate inceptionDate = TKF100.getInceptionDate();
    BigDecimal inceptionCash = new BigDecimal("5000000.00");

    navPositionLedger.recordPositions(TKF100, inceptionDate, Map.of(), inceptionCash, ZERO, ZERO);

    fundPositionRepository.save(
        FundPosition.builder()
            .navDate(inceptionDate)
            .fund(TKF100)
            .accountType(CASH)
            .accountName("Cash")
            .marketValue(ZERO)
            .currency("EUR")
            .createdAt(Instant.now())
            .build());

    insertFeeRate(TKF100, "MANAGEMENT", new BigDecimal("0.0029"), inceptionDate);
    insertFeeRate(TKF100, "DEPOT", new BigDecimal("0.00035"), inceptionDate);
    issueFundUnits(new BigDecimal("1000000.000"), inceptionDate);
    entityManager.flush();
    entityManager.clear();

    var result = navCalculationService.computeFeeBaseValue(TKF100, inceptionDate);
    assertThat(result).isPresent();
    assertThat(result.get().baseValue()).isEqualByComparingTo(inceptionCash);
  }

  @Test
  void computeFeeBaseValue_nonInceptionDayUsesOnlyPreviousDayPosition() {
    LocalDate feb2 = TKF100.getInceptionDate(); // Feb 2, Monday
    LocalDate feb3 = LocalDate.of(2026, 2, 3); // Tuesday

    BigDecimal feb2Cash = new BigDecimal("5000000.00");
    BigDecimal feb3CashDelta = new BigDecimal("1000000.00");

    navPositionLedger.recordPositions(TKF100, feb2, Map.of(), feb2Cash, ZERO, ZERO);
    navPositionLedger.recordPositions(TKF100, feb3, Map.of(), feb3CashDelta, ZERO, ZERO);

    fundPositionRepository.save(
        FundPosition.builder()
            .navDate(feb2)
            .fund(TKF100)
            .accountType(CASH)
            .accountName("Cash")
            .marketValue(ZERO)
            .currency("EUR")
            .createdAt(Instant.now())
            .build());

    insertFeeRate(TKF100, "MANAGEMENT", new BigDecimal("0.0029"), feb2);
    insertFeeRate(TKF100, "DEPOT", new BigDecimal("0.00035"), feb2);
    issueFundUnits(new BigDecimal("1000000.000"), feb2);
    entityManager.flush();
    entityManager.clear();

    // Feb 3's fee should use only Feb 2's position (5M), NOT cumulative (6M)
    var feb3Result = navCalculationService.computeFeeBaseValue(TKF100, feb3);
    assertThat(feb3Result).isPresent();
    assertThat(feb3Result.get().baseValue()).isEqualByComparingTo(feb2Cash);
  }

  @Test
  void computeFeeBaseValue_weekendUsesFridayPositions() {
    LocalDate thu = LocalDate.of(2026, 2, 5);
    LocalDate fri = LocalDate.of(2026, 2, 6);
    LocalDate saturday = LocalDate.of(2026, 2, 7);
    BigDecimal thuCash = new BigDecimal("5000000.00");
    BigDecimal friCash = new BigDecimal("6000000.00");

    saveFundPosition(thu, CASH, "Cash", thuCash);
    saveFundPosition(fri, CASH, "Cash", friCash);
    entityManager.flush();

    fundPositionLedgerService.recordPositionsToLedger(TKF100, thu);
    fundPositionLedgerService.recordPositionsToLedger(TKF100, fri);

    insertFeeRate(TKF100, "MANAGEMENT", new BigDecimal("0.0029"), thu);
    insertFeeRate(TKF100, "DEPOT", new BigDecimal("0.00035"), thu);
    issueFundUnits(new BigDecimal("1000000.000"), thu);
    entityManager.flush();
    entityManager.clear();

    var result = navCalculationService.computeFeeBaseValue(TKF100, saturday);
    assertThat(result).isPresent();
    assertThat(result.get().baseValue()).isEqualByComparingTo(friCash);
  }

  @Test
  void multiDayPositionBackfill_producesCorrectLedgerBalancesPerDay() {
    // Position dates (Tue-Thu)
    LocalDate pos1 = LocalDate.of(2026, 2, 3);
    LocalDate pos2 = LocalDate.of(2026, 2, 4);
    LocalDate pos3 = LocalDate.of(2026, 2, 5);
    // Calculation dates = next working day after position date
    LocalDate calc1 = LocalDate.of(2026, 2, 4);
    LocalDate calc2 = LocalDate.of(2026, 2, 5);
    LocalDate calc3 = LocalDate.of(2026, 2, 6);
    BigDecimal cash1 = new BigDecimal("5000000.00");
    BigDecimal cash2 = new BigDecimal("5100000.00");
    BigDecimal cash3 = new BigDecimal("5200000.00");

    saveFundPosition(pos1, CASH, "Cash", cash1);
    saveFundPosition(pos2, CASH, "Cash", cash2);
    saveFundPosition(pos3, CASH, "Cash", cash3);
    entityManager.flush();

    fundPositionLedgerService.recordPositionsToLedger(TKF100, pos1);
    fundPositionLedgerService.recordPositionsToLedger(TKF100, pos2);
    fundPositionLedgerService.recordPositionsToLedger(TKF100, pos3);

    insertFeeRate(TKF100, "MANAGEMENT", new BigDecimal("0.0029"), pos1);
    insertFeeRate(TKF100, "DEPOT", new BigDecimal("0.00035"), pos1);
    issueFundUnits(new BigDecimal("1000000.000"), pos1);
    insertPrices(calc3);
    entityManager.flush();
    entityManager.clear();

    var result1 = navCalculationService.calculate(TKF100, calc1);
    assertThat(result1.cashPosition()).isEqualByComparingTo(cash1);

    var result2 = navCalculationService.calculate(TKF100, calc2);
    assertThat(result2.cashPosition()).isEqualByComparingTo(cash2);

    var result3 = navCalculationService.calculate(TKF100, calc3);
    assertThat(result3.cashPosition()).isEqualByComparingTo(cash3);
  }

  @Test
  void multiDayPositionBackfill_feeBaseValuesMatchCorrectDayPositions() {
    LocalDate day1 = LocalDate.of(2026, 2, 3);
    LocalDate day2 = LocalDate.of(2026, 2, 4);
    BigDecimal day1Cash = new BigDecimal("5000000.00");
    BigDecimal day2Cash = new BigDecimal("6000000.00");

    saveFundPosition(day1, CASH, "Cash", day1Cash);
    saveFundPosition(day2, CASH, "Cash", day2Cash);
    entityManager.flush();

    fundPositionLedgerService.recordPositionsToLedger(TKF100, day1);
    fundPositionLedgerService.recordPositionsToLedger(TKF100, day2);

    insertFeeRate(TKF100, "MANAGEMENT", new BigDecimal("0.0029"), day1);
    insertFeeRate(TKF100, "DEPOT", new BigDecimal("0.00035"), day1);
    issueFundUnits(new BigDecimal("1000000.000"), day1);
    entityManager.flush();
    entityManager.clear();

    var day2FeeBase = navCalculationService.computeFeeBaseValue(TKF100, day2);
    assertThat(day2FeeBase).isPresent();
    assertThat(day2FeeBase.get().baseValue()).isEqualByComparingTo(day1Cash);
  }

  @Test
  void computeFeeBaseValue_includesPendingSubscriptions() {
    LocalDate posDate = LocalDate.of(2026, 2, 5);
    LocalDate calcDate = LocalDate.of(2026, 2, 6);
    BigDecimal cash = new BigDecimal("5000000.00");
    BigDecimal pendingSubs = new BigDecimal("24816.87");

    saveFundPosition(posDate, CASH, "Cash", cash);
    fundPositionRepository.save(
        FundPosition.builder()
            .navDate(posDate)
            .fund(TKF100)
            .accountType(RECEIVABLES)
            .accountName("Receivables of outstanding units")
            .accountId(TKF100.getIsin())
            .marketValue(pendingSubs)
            .currency("EUR")
            .createdAt(Instant.now())
            .build());
    entityManager.flush();

    fundPositionLedgerService.recordPositionsToLedger(TKF100, posDate);

    insertFeeRate(TKF100, "MANAGEMENT", new BigDecimal("0.0029"), posDate);
    insertFeeRate(TKF100, "DEPOT", new BigDecimal("0.00035"), posDate);
    issueFundUnits(new BigDecimal("1000000.000"), posDate);
    entityManager.flush();
    entityManager.clear();

    var result = navCalculationService.computeFeeBaseValue(TKF100, calcDate);
    assertThat(result).isPresent();
    assertThat(result.get().baseValue()).isEqualByComparingTo(cash.add(pendingSubs));
  }

  @Test
  void feeCalculationRecordsFeesPerFundInLedger() {
    LocalDate date = LocalDate.of(2025, 3, 15);
    BigDecimal tkf100Aum = new BigDecimal("50000000");
    BigDecimal tuk75Aum = new BigDecimal("1000000000");

    insertSecurityPosition(TKF100, LocalDate.of(2025, 2, 28), new BigDecimal("48000000"));
    insertSecurityPosition(TUK75, LocalDate.of(2025, 2, 28), new BigDecimal("980000000"));

    insertFeeRate(TKF100, "MANAGEMENT", new BigDecimal("0.0025"));
    insertFeeRate(TUK75, "MANAGEMENT", new BigDecimal("0.0025"));
    insertDepotFeeTier(new BigDecimal("0.00035"));

    Instant feeCutoff =
        date.plusDays(1).atStartOfDay().atZone(ZoneId.of("Europe/Tallinn")).toInstant();
    feeCalculationService.calculateFeesForNav(TKF100, date, tkf100Aum, feeCutoff, null);
    feeCalculationService.calculateFeesForNav(TUK75, date, tuk75Aum, feeCutoff, null);

    BigDecimal tkf100MgmtBalance = getSystemAccountBalance(MANAGEMENT_FEE_ACCRUAL, TKF100);
    BigDecimal tuk75MgmtBalance = getSystemAccountBalance(MANAGEMENT_FEE_ACCRUAL, TUK75);
    BigDecimal tkf100DepotBalance = getSystemAccountBalance(DEPOT_FEE_ACCRUAL, TKF100);

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

    BigDecimal tuk75ManagementFee =
        jdbcClient
            .sql(
                """
                SELECT daily_amount_net FROM investment_fee_accrual
                WHERE fund_code = 'TUK75' AND fee_type = 'MANAGEMENT' AND accrual_date = :date
                """)
            .param("date", date)
            .query(BigDecimal.class)
            .single();

    assertThat(tkf100MgmtBalance.abs().setScale(2, HALF_UP))
        .isEqualByComparingTo(tkf100ManagementFee.setScale(2, HALF_UP));
    assertThat(tuk75MgmtBalance.abs().setScale(2, HALF_UP))
        .isEqualByComparingTo(tuk75ManagementFee.setScale(2, HALF_UP));
    assertThat(tkf100DepotBalance).isNotEqualByComparingTo(ZERO);
    assertThat(tkf100MgmtBalance).isNotEqualByComparingTo(tuk75MgmtBalance);
  }

  private void insertSecurityPosition(TulevaFund fund, LocalDate date, BigDecimal marketValue) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_fund_position
            (nav_date, fund_code, account_type, account_name, account_id, market_value)
            VALUES (:navDate, :fundCode, 'SECURITY', :accountId, :accountId, :marketValue)
            """)
        .param("navDate", date)
        .param("fundCode", fund.name())
        .param("accountId", "TEST_ISIN_" + fund.name())
        .param("marketValue", marketValue)
        .update();
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
    return getSystemAccountBalance(systemAccount, TKF100);
  }

  private BigDecimal getSystemAccountBalance(SystemAccount systemAccount, TulevaFund fund) {
    return jdbcClient
        .sql(
            """
            SELECT COALESCE(SUM(e.amount), 0)
            FROM ledger.entry e
            JOIN ledger.account a ON e.account_id = a.id
            WHERE a.name = :accountName
            """)
        .param("accountName", systemAccount.getAccountName(fund))
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
    fundPositionImportService.importNewPositions(positions);
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
        TKF100,
        navData.navDate,
        securitiesUnits,
        navData.cashPosition,
        navData.tradeReceivables,
        navData.tradePayables);
  }

  @SneakyThrows
  private void insertPrices(LocalDate calculationDate) {
    var priceLines = Files.readAllLines(Path.of("src/test/resources/nav-test-data/prices.csv"));
    for (int i = 1; i < priceLines.size(); i++) {
      String[] parts = priceLines.get(i).split(",");
      LocalDate priceDate = LocalDate.parse(parts[1]);
      if (priceDate.isBefore(calculationDate)) {
        Instant updatedAt = priceDate.atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant();
        entityManager
            .createNativeQuery(
                "INSERT INTO index_values (key, date, value, provider, updated_at) VALUES (:key, :date, :value, 'EODHD', :updatedAt)")
            .setParameter("key", parts[0])
            .setParameter("date", priceDate)
            .setParameter("value", new BigDecimal(parts[2]))
            .setParameter("updatedAt", Timestamp.from(updatedAt))
            .executeUpdate();
      }
    }
  }

  private void issueFundUnits(BigDecimal unitsOutstanding, LocalDate navDate) {
    LedgerAccount fundUnitsOutstandingAccount =
        ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING, TKF100);
    LedgerAccount userFundUnitsAccount =
        ledgerService.getPartyAccount(testUser.getPersonalCode(), PERSON, FUND_UNITS);

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

  private void saveFundPosition(
      LocalDate navDate, AccountType accountType, String accountName, BigDecimal marketValue) {
    fundPositionRepository.save(
        FundPosition.builder()
            .navDate(navDate)
            .fund(TKF100)
            .accountType(accountType)
            .accountName(accountName)
            .marketValue(marketValue)
            .currency("EUR")
            .createdAt(Instant.now())
            .build());
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
