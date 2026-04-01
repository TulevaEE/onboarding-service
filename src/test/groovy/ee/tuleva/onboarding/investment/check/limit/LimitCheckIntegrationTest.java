package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.*;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.time.ClockHolder;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * Key formulas from the AppScript:
 *
 * <ul>
 *   <li>position % = security_market_value / total_fund_nav (securities + cash + liabilities +
 *       receivables)
 *   <li>reserve check: raw cash balance ("kontojääk") vs soft/hard limits (mode=min)
 *   <li>free cash: cash + liabilities - reserve_soft (liabilities are negative)
 * </ul>
 */
@SpringBootTest
@Transactional
@Disabled
class LimitCheckIntegrationTest {

  private static final LocalDate NAV_DATE = LocalDate.of(2026, 3, 2);
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-03-03T10:00:00Z"), ZoneId.of("Europe/Tallinn"));

  @Autowired private LimitCheckService limitCheckService;
  @Autowired private LimitCheckEventRepository limitCheckEventRepository;
  @Autowired private JdbcClient jdbcClient;

  @BeforeEach
  void setUp() {
    ClockHolder.setClock(FIXED_CLOCK);
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void tuk75MatchesSpreadsheetCalculations() {
    insertTuk75Data();

    var results = limitCheckService.runChecks();

    var tuk75 = results.stream().filter(r -> r.fund() == TUK75).findFirst().orElseThrow();

    // -- Position checks: weight = security_value / total_nav (AppScript: agg.byIsin[isin] /
    // agg.total) --
    // Total NAV = 9_978_000 + 25_000 + (-3_000) + 0 = 10_000_000

    // IE00BFG1TM61: 2_899_000 / 10_000_000 = 28.99% < soft 29.65% → OK
    var dwBreach =
        tuk75.positionBreaches().stream()
            .filter(b -> b.isin().equals("IE00BFG1TM61"))
            .findFirst()
            .orElseThrow();
    assertThat(dwBreach.severity()).isEqualTo(OK);
    assertThat(dwBreach.actualPercent()).isEqualByComparingTo(new BigDecimal("28.99"));

    // IE0009FT4LX4: 2_914_000 / 10_000_000 = 29.14% < soft 29.65% → OK
    var ccfBreach =
        tuk75.positionBreaches().stream()
            .filter(b -> b.isin().equals("IE0009FT4LX4"))
            .findFirst()
            .orElseThrow();
    assertThat(ccfBreach.severity()).isEqualTo(OK);
    assertThat(ccfBreach.actualPercent()).isEqualByComparingTo(new BigDecimal("29.14"));

    // IE00BFNM3G45: 2_203_000 / 10_000_000 = 22.03% < soft 24.18% → OK
    var usaBreach =
        tuk75.positionBreaches().stream()
            .filter(b -> b.isin().equals("IE00BFNM3G45"))
            .findFirst()
            .orElseThrow();
    assertThat(usaBreach.severity()).isEqualTo(OK);
    assertThat(usaBreach.actualPercent()).isEqualByComparingTo(new BigDecimal("22.03"));

    // IE00BFNM3D14: 780_000 / 10_000_000 = 7.80% < soft 8.13% → OK
    var europeBreach =
        tuk75.positionBreaches().stream()
            .filter(b -> b.isin().equals("IE00BFNM3D14"))
            .findFirst()
            .orElseThrow();
    assertThat(europeBreach.severity()).isEqualTo(OK);
    assertThat(europeBreach.actualPercent()).isEqualByComparingTo(new BigDecimal("7.80"));

    // IE00BFNM3L97: 87_000 / 10_000_000 = 0.87% > soft 0.86% → SOFT
    var japanBreach =
        tuk75.positionBreaches().stream()
            .filter(b -> b.isin().equals("IE00BFNM3L97"))
            .findFirst()
            .orElseThrow();
    assertThat(japanBreach.severity()).isEqualTo(SOFT);
    assertThat(japanBreach.actualPercent()).isEqualByComparingTo(new BigDecimal("0.87"));

    // IE00BKPTWY98: 1_095_000 / 10_000_000 = 10.95% > soft 10.70% → SOFT
    var emBreach =
        tuk75.positionBreaches().stream()
            .filter(b -> b.isin().equals("IE00BKPTWY98"))
            .findFirst()
            .orElseThrow();
    assertThat(emBreach.severity()).isEqualTo(SOFT);
    assertThat(emBreach.actualPercent()).isEqualByComparingTo(new BigDecimal("10.95"));

    // -- Reserve check: raw cash balance, NOT net cash (AppScript: agg.cash with mode=min) --
    // cash = 25_000 > soft 5_000 → OK
    assertThat(tuk75.reserveBreach()).isNotNull();
    assertThat(tuk75.reserveBreach().severity()).isEqualTo(OK);
    assertThat(tuk75.reserveBreach().cashBalance()).isEqualByComparingTo(new BigDecimal("25000"));

    // -- Free cash check (AppScript: cash + liabilities - reserve_soft) --
    // 25_000 + (-3_000) - 5_000 = 17_000. 17_000 > max 10_000 → HARD
    assertThat(tuk75.freeCashBreach()).isNotNull();
    assertThat(tuk75.freeCashBreach().severity()).isEqualTo(HARD);
    assertThat(tuk75.freeCashBreach().freeCash()).isEqualByComparingTo(new BigDecimal("17000"));
  }

  @Test
  void tuk00IndexGroupAggregation() {
    insertTuk00Data();

    var results = limitCheckService.runChecks();

    var tuk00 = results.stream().filter(r -> r.fund() == TUK00).findFirst().orElseThrow();

    // Euro Aggregate indeks: (2_800_000 + 2_700_000) / 10_000_000 = 55.00%
    // 55.00% > soft 54.50% → SOFT, but not > hard 55.00% (strict >)
    var euroAgg =
        tuk00.positionBreaches().stream()
            .filter(b -> b.isin().equals("Euro Aggregate indeks"))
            .findFirst()
            .orElseThrow();
    assertThat(euroAgg.severity()).isEqualTo(SOFT);
    assertThat(euroAgg.actualPercent()).isEqualByComparingTo(new BigDecimal("55"));

    // Global Aggregate indeks: (2_200_000 + 2_200_000) / 10_000_000 = 44.00% → OK
    var globalAgg =
        tuk00.positionBreaches().stream()
            .filter(b -> b.isin().equals("Global Aggregate indeks"))
            .findFirst()
            .orElseThrow();
    assertThat(globalAgg.severity()).isEqualTo(OK);
    assertThat(globalAgg.actualPercent()).isEqualByComparingTo(new BigDecimal("44"));

    // Individual position: LU0826455353 = 2_800_000 / 10_000_000 = 28.00% > soft 26.75% → SOFT
    var euroAggBond =
        tuk00.positionBreaches().stream()
            .filter(b -> "LU0826455353".equals(b.isin()))
            .findFirst()
            .orElseThrow();
    assertThat(euroAggBond.severity()).isEqualTo(SOFT);
  }

  @Test
  void rerunReplacesExistingEventsInsteadOfCreatingDuplicates() {
    insertTuk75Data();

    limitCheckService.runChecks();
    limitCheckService.runChecks();

    var events = limitCheckEventRepository.findByFundAndCheckDate(TUK75, NAV_DATE);
    assertThat(events).hasSize(4);
    assertThat(events)
        .extracting(LimitCheckEvent::getCheckType)
        .containsExactlyInAnyOrder(
            CheckType.POSITION, CheckType.PROVIDER, CheckType.RESERVE, CheckType.FREE_CASH);
  }

  @Test
  void denominatorUsesTotalFundNavNotSecuritiesOnly() {
    insertTuk75Data();

    var results = limitCheckService.runChecks();
    var tuk75 = results.stream().filter(r -> r.fund() == TUK75).findFirst().orElseThrow();

    // If we incorrectly used securities-only NAV (9_978_000), the % would be:
    // IE00BFG1TM61: 2_899_000 / 9_978_000 = 29.05% (wrong)
    // With total fund NAV (10_000_000): 2_899_000 / 10_000_000 = 28.99% (correct per AppScript)
    var dwBreach =
        tuk75.positionBreaches().stream()
            .filter(b -> b.isin().equals("IE00BFG1TM61"))
            .findFirst()
            .orElseThrow();

    var correctPercent =
        new BigDecimal("2899000")
            .multiply(BigDecimal.valueOf(100))
            .divide(new BigDecimal("10000000"), 4, RoundingMode.HALF_UP);
    assertThat(dwBreach.actualPercent()).isEqualByComparingTo(correctPercent);

    var wrongPercent =
        new BigDecimal("2899000")
            .multiply(BigDecimal.valueOf(100))
            .divide(new BigDecimal("9978000"), 4, RoundingMode.HALF_UP);
    assertThat(dwBreach.actualPercent()).isNotEqualByComparingTo(wrongPercent);
  }

  // -- TUK75 test data: mimics CSV row for TUK75 on 2026-03-02 --
  // Total NAV = 10_000_000 (securities 9_978_000 + cash 25_000 + liabilities -3_000)
  private void insertTuk75Data() {
    // Securities
    record Security(String isin, String label, long marketValue, double soft, double hard) {}

    var securities =
        new Security[] {
          new Security("IE00BFG1TM61", "iShares Developed World Screened", 2_899_000, 29.65, 30.0),
          new Security("IE0009FT4LX4", "CCF Developed World Screened", 2_914_000, 29.65, 30.0),
          new Security("IE00BFNM3G45", "iShares MSCI USA Screened", 2_203_000, 24.18, 25.99),
          new Security("IE00BFNM3D14", "iShares MSCI Europe Screened", 780_000, 8.13, 8.74),
          new Security("IE00BFNM3L97", "iShares MSCI Japan Screened", 87_000, 0.86, 0.92),
          new Security("IE00BKPTWY98", "iShares EM Screened", 1_095_000, 10.70, 11.50),
        };

    for (var s : securities) {
      insertFundPosition("TUK75", NAV_DATE, "SECURITY", s.isin, s.marketValue);
      insertPositionLimit("TUK75", s.isin, s.label, null, s.soft, s.hard);
    }

    // Cash and liabilities (FundPosition only — stored as negative per production convention)
    insertFundPosition("TUK75", NAV_DATE, "CASH", "CASH_ACCOUNT", 25_000);
    insertFundPosition("TUK75", NAV_DATE, "LIABILITY", "LIABILITY_ACCOUNT", -3_000);

    // Fund limits (reserve + free cash)
    insertFundLimit("TUK75", 5_000, 3_000, 10_000);

    // Model portfolio allocations (all ISHARES for TUK75)
    for (var s : securities) {
      insertModelPortfolioAllocation("TUK75", s.isin, "ISHARES");
    }
  }

  // -- TUK00 test data: bond fund with index groups --
  // Total NAV = 10_000_000 (securities 9_900_000 + cash 105_000 + liabilities -5_000)
  private void insertTuk00Data() {
    record Security(
        String isin, String label, long marketValue, String indexGroup, double soft, double hard) {}

    var securities =
        new Security[] {
          new Security(
              "LU0826455353",
              "iShares Euro Aggregate Bond",
              2_800_000,
              "Euro Aggregate indeks",
              26.75,
              28.75),
          new Security(
              "IE0031080751",
              "iShares Euro Government Bond",
              2_700_000,
              "Euro Aggregate indeks",
              26.75,
              28.75),
          new Security(
              "LU0839970364",
              "iShares Global Government Bond",
              2_200_000,
              "Global Aggregate indeks",
              26.75,
              28.75),
          new Security(
              "IE0005032192",
              "iShares Euro Credit Bond",
              2_200_000,
              "Global Aggregate indeks",
              26.75,
              28.75),
        };

    for (var s : securities) {
      insertFundPosition("TUK00", NAV_DATE, "SECURITY", s.isin, s.marketValue);
      insertPositionLimit("TUK00", s.isin, s.label, s.indexGroup, s.soft, s.hard);
    }

    // Index aggregate limits (isin = NULL)
    insertPositionLimit(
        "TUK00", null, "Euro Aggregate indeks", "Euro Aggregate indeks", 54.50, 55.0);
    insertPositionLimit(
        "TUK00", null, "Global Aggregate indeks", "Global Aggregate indeks", 54.50, 55.0);

    insertFundPosition("TUK00", NAV_DATE, "CASH", "CASH_ACCOUNT", 105_000);
    insertFundPosition("TUK00", NAV_DATE, "LIABILITY", "LIABILITY_ACCOUNT", -5_000);

    insertFundLimit("TUK00", 100_000, 50_000, 1_000_000);

    for (var s : securities) {
      insertModelPortfolioAllocation("TUK00", s.isin, "ISHARES");
    }
  }

  // -- Helper methods --

  private void insertFundPosition(
      String fund, LocalDate navDate, String accountType, String accountId, long marketValue) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_fund_position
            (nav_date, fund_code, account_type, account_name, account_id, market_value)
            VALUES (:navDate, :fund, :accountType, :accountId, :accountId, :marketValue)
            """)
        .param("navDate", navDate)
        .param("fund", fund)
        .param("accountType", accountType)
        .param("accountId", accountId)
        .param("marketValue", BigDecimal.valueOf(marketValue))
        .update();
  }

  private void insertPositionLimit(
      String fund,
      String isin,
      String label,
      String indexGroup,
      double softPercent,
      double hardPercent) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_position_limit
            (effective_date, fund_code, isin, label, index_group, soft_limit_percent, hard_limit_percent)
            VALUES (:effectiveDate, :fund, :isin, :label, :indexGroup, :soft, :hard)
            """)
        .param("effectiveDate", LocalDate.of(2025, 6, 30))
        .param("fund", fund)
        .param("isin", isin)
        .param("label", label)
        .param("indexGroup", indexGroup)
        .param("soft", BigDecimal.valueOf(softPercent))
        .param("hard", BigDecimal.valueOf(hardPercent))
        .update();
  }

  private void insertFundLimit(
      String fund, double reserveSoft, double reserveHard, double maxFreeCash) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_fund_limit
            (effective_date, fund_code, reserve_soft, reserve_hard, max_free_cash)
            VALUES (:effectiveDate, :fund, :reserveSoft, :reserveHard, :maxFreeCash)
            """)
        .param("effectiveDate", LocalDate.of(2025, 6, 30))
        .param("fund", fund)
        .param("reserveSoft", BigDecimal.valueOf(reserveSoft))
        .param("reserveHard", BigDecimal.valueOf(reserveHard))
        .param("maxFreeCash", BigDecimal.valueOf(maxFreeCash))
        .update();
  }

  private void insertModelPortfolioAllocation(String fund, String isin, String provider) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_model_portfolio_allocation
            (effective_date, fund_code, isin, provider, weight)
            VALUES (:effectiveDate, :fund, :isin, :provider, 1)
            """)
        .param("effectiveDate", LocalDate.of(2025, 6, 30))
        .param("fund", fund)
        .param("isin", isin)
        .param("provider", provider)
        .update();
  }
}
