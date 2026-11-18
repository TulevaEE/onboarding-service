package ee.tuleva.onboarding.investment.config;

import static ee.tuleva.onboarding.investment.config.InvestmentParameter.SEB_FUND_CONFIRMATION_NAV_TOLERANCE;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.TRACKING_BREACH_THRESHOLD;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.TRACKING_MAX_DAILY_RETURN;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
@Import(InvestmentParameterRepository.class)
class InvestmentParameterRepositoryTest {

  private static final String ISIN = "ZZ0000000001";
  private static final String OTHER_ISIN = "ZZ0000000002";

  @Autowired private JdbcClient jdbcClient;
  @Autowired private InvestmentParameterRepository repository;

  @BeforeEach
  void setUp() {
    jdbcClient.sql("DELETE FROM investment_parameter").update();
  }

  @Test
  void findLatestValue_globalScope_returnsRowForAsOfDate() {
    insert(TRACKING_BREACH_THRESHOLD, null, new BigDecimal("0.01"), LocalDate.of(2025, 1, 1));

    BigDecimal value =
        repository.findLatestValue(TRACKING_BREACH_THRESHOLD, LocalDate.of(2025, 6, 15));

    assertThat(value).isEqualByComparingTo(new BigDecimal("0.01"));
  }

  @Test
  void findLatestValue_globalScope_returnsLatestOfMultipleRows() {
    insert(TRACKING_BREACH_THRESHOLD, null, new BigDecimal("0.01"), LocalDate.of(2024, 1, 1));
    insert(TRACKING_BREACH_THRESHOLD, null, new BigDecimal("0.02"), LocalDate.of(2025, 1, 1));

    BigDecimal value =
        repository.findLatestValue(TRACKING_BREACH_THRESHOLD, LocalDate.of(2025, 6, 15));

    assertThat(value).isEqualByComparingTo(new BigDecimal("0.02"));
  }

  @Test
  void findLatestValue_globalScope_ignoresFutureEffectiveDate() {
    insert(TRACKING_BREACH_THRESHOLD, null, new BigDecimal("0.01"), LocalDate.of(2024, 1, 1));
    insert(TRACKING_BREACH_THRESHOLD, null, new BigDecimal("0.99"), LocalDate.of(2030, 1, 1));

    BigDecimal value =
        repository.findLatestValue(TRACKING_BREACH_THRESHOLD, LocalDate.of(2025, 6, 15));

    assertThat(value).isEqualByComparingTo(new BigDecimal("0.01"));
  }

  @Test
  void findLatestValue_globalScope_ignoresFundScopedRows() {
    insert(TRACKING_BREACH_THRESHOLD, TUK75, new BigDecimal("0.99"), LocalDate.of(2025, 1, 1));

    assertThatThrownBy(
            () -> repository.findLatestValue(TRACKING_BREACH_THRESHOLD, LocalDate.of(2025, 6, 15)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No investment parameter found")
        .hasMessageContaining("parameter=TRACKING_BREACH_THRESHOLD")
        .hasMessageContaining("fund=GLOBAL");
  }

  @Test
  void findLatestValueIfPresent_globalScope_returnsTheValue() {
    insert(TRACKING_BREACH_THRESHOLD, null, new BigDecimal("0.01"), LocalDate.of(2025, 1, 1));

    assertThat(
            repository.findLatestValueIfPresent(
                TRACKING_BREACH_THRESHOLD, LocalDate.of(2025, 6, 15)))
        .hasValueSatisfying(
            value -> assertThat(value).isEqualByComparingTo(new BigDecimal("0.01")));
  }

  @Test
  void findLatestValueIfPresent_globalScope_isEmptyWhenNoRowIsEffectiveYet() {
    insert(TRACKING_BREACH_THRESHOLD, null, new BigDecimal("0.01"), LocalDate.of(2025, 1, 1));

    assertThat(
            repository.findLatestValueIfPresent(
                TRACKING_BREACH_THRESHOLD, LocalDate.of(2024, 6, 15)))
        .isEmpty();
  }

  @Test
  void findLatestValue_fundScope_returnsRowForFund() {
    insert(TRACKING_MAX_DAILY_RETURN, TUK75, new BigDecimal("0.5"), LocalDate.of(2025, 1, 1));
    insert(TRACKING_MAX_DAILY_RETURN, TKF100, new BigDecimal("0.6"), LocalDate.of(2025, 1, 1));

    BigDecimal value =
        repository.findLatestValue(TRACKING_MAX_DAILY_RETURN, TUK75, LocalDate.of(2025, 6, 15));

    assertThat(value).isEqualByComparingTo(new BigDecimal("0.5"));
  }

  @Test
  void findLatestValue_fundScope_ignoresGlobalRows() {
    insert(TRACKING_MAX_DAILY_RETURN, null, new BigDecimal("0.1"), LocalDate.of(2025, 1, 1));

    assertThatThrownBy(
            () ->
                repository.findLatestValue(
                    TRACKING_MAX_DAILY_RETURN, TUK75, LocalDate.of(2025, 6, 15)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("fund=TUK75");
  }

  // The table has no unique constraint, so a re-seed can leave two rows on the same date. Without
  // a tiebreaker the threshold a check runs against is whichever row the database happens to
  // return first, and the same check can pass or breach on identical data.
  @Test
  void findLatestValue_globalScope_takesTheLastWrittenOfTwoRowsOnTheSameDate() {
    insert(TRACKING_BREACH_THRESHOLD, null, new BigDecimal("0.01"), LocalDate.of(2025, 1, 1));
    insert(TRACKING_BREACH_THRESHOLD, null, new BigDecimal("0.02"), LocalDate.of(2025, 1, 1));

    BigDecimal value =
        repository.findLatestValue(TRACKING_BREACH_THRESHOLD, LocalDate.of(2025, 6, 15));

    assertThat(value).isEqualByComparingTo(new BigDecimal("0.02"));
  }

  @Test
  void findLatestValue_fundScope_takesTheLastWrittenOfTwoRowsOnTheSameDate() {
    insert(TRACKING_MAX_DAILY_RETURN, TUK75, new BigDecimal("0.5"), LocalDate.of(2025, 1, 1));
    insert(TRACKING_MAX_DAILY_RETURN, TUK75, new BigDecimal("0.7"), LocalDate.of(2025, 1, 1));

    BigDecimal value =
        repository.findLatestValue(TRACKING_MAX_DAILY_RETURN, TUK75, LocalDate.of(2025, 6, 15));

    assertThat(value).isEqualByComparingTo(new BigDecimal("0.7"));
  }

  @Test
  void findLatestValue_throwsWhenNoRowMatches() {
    assertThatThrownBy(
            () -> repository.findLatestValue(TRACKING_BREACH_THRESHOLD, LocalDate.of(2025, 6, 15)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No investment parameter found")
        .hasMessageContaining("parameter=TRACKING_BREACH_THRESHOLD")
        .hasMessageContaining("asOf=2025-06-15");
  }

  @Test
  void findLatestValueIfPresent_fundScope_returnsEmptyWhenNoRowMatches() {
    assertThat(
            repository.findLatestValueIfPresent(
                TRACKING_MAX_DAILY_RETURN, TUK75, LocalDate.of(2025, 6, 15)))
        .isEmpty();
  }

  @Test
  void findLatestValueIfPresent_fundScope_returnsTheLatestRowForTheFund() {
    insert(TRACKING_MAX_DAILY_RETURN, TUK75, new BigDecimal("0.5"), LocalDate.of(2024, 1, 1));
    insert(TRACKING_MAX_DAILY_RETURN, TUK75, new BigDecimal("0.7"), LocalDate.of(2025, 1, 1));
    insert(TRACKING_MAX_DAILY_RETURN, TKF100, new BigDecimal("0.9"), LocalDate.of(2025, 1, 1));

    assertThat(
            repository.findLatestValueIfPresent(
                TRACKING_MAX_DAILY_RETURN, TUK75, LocalDate.of(2025, 6, 15)))
        .hasValueSatisfying(value -> assertThat(value).isEqualByComparingTo(new BigDecimal("0.7")));
  }

  @Test
  void findLatestValueIfPresent_fundScope_ignoresGlobalRows() {
    insert(TRACKING_MAX_DAILY_RETURN, null, new BigDecimal("0.1"), LocalDate.of(2025, 1, 1));

    assertThat(
            repository.findLatestValueIfPresent(
                TRACKING_MAX_DAILY_RETURN, TUK75, LocalDate.of(2025, 6, 15)))
        .isEmpty();
  }

  @Test
  void findLatestValue_globalScope_ignoresIsinScopedRows() {
    insert(TRACKING_BREACH_THRESHOLD, null, new BigDecimal("0.01"), LocalDate.of(2024, 1, 1));
    insertForIsin(
        TRACKING_BREACH_THRESHOLD, ISIN, new BigDecimal("0.99"), LocalDate.of(2025, 1, 1));

    BigDecimal value =
        repository.findLatestValue(TRACKING_BREACH_THRESHOLD, LocalDate.of(2025, 6, 15));

    assertThat(value).isEqualByComparingTo(new BigDecimal("0.01"));
  }

  @Test
  void findLatestValueIfPresent_globalScope_isEmptyWhenOnlyAnIsinRowExists() {
    insertForIsin(
        TRACKING_BREACH_THRESHOLD, ISIN, new BigDecimal("0.99"), LocalDate.of(2025, 1, 1));

    assertThat(
            repository.findLatestValueIfPresent(
                TRACKING_BREACH_THRESHOLD, LocalDate.of(2025, 6, 15)))
        .isEmpty();
  }

  @Test
  void findLatestValue_fundScope_ignoresARowThatAlsoNamesAnIsin() {
    insert(TRACKING_MAX_DAILY_RETURN, TUK75, new BigDecimal("0.5"), LocalDate.of(2024, 1, 1));
    insertForFundAndIsin(
        TRACKING_MAX_DAILY_RETURN, TUK75, ISIN, new BigDecimal("0.99"), LocalDate.of(2025, 1, 1));

    BigDecimal value =
        repository.findLatestValue(TRACKING_MAX_DAILY_RETURN, TUK75, LocalDate.of(2025, 6, 15));

    assertThat(value).isEqualByComparingTo(new BigDecimal("0.5"));
  }

  @Test
  void findLatestValueForIsinOrGlobal_takesTheIsinsRowOverTheGlobalOne() {
    insert(
        SEB_FUND_CONFIRMATION_NAV_TOLERANCE,
        null,
        new BigDecimal("0.004"),
        LocalDate.of(2025, 1, 1));
    insertForIsin(
        SEB_FUND_CONFIRMATION_NAV_TOLERANCE,
        ISIN,
        new BigDecimal("0.009"),
        LocalDate.of(2025, 1, 1));

    BigDecimal value =
        repository.findLatestValueForIsinOrGlobal(
            SEB_FUND_CONFIRMATION_NAV_TOLERANCE, ISIN, LocalDate.of(2025, 6, 15));

    assertThat(value).isEqualByComparingTo(new BigDecimal("0.009"));
  }

  @Test
  void findLatestValueForIsinOrGlobal_fallsBackToTheGlobalRowForAnIsinWithoutItsOwn() {
    insert(
        SEB_FUND_CONFIRMATION_NAV_TOLERANCE,
        null,
        new BigDecimal("0.004"),
        LocalDate.of(2025, 1, 1));
    insertForIsin(
        SEB_FUND_CONFIRMATION_NAV_TOLERANCE,
        OTHER_ISIN,
        new BigDecimal("0.009"),
        LocalDate.of(2025, 1, 1));

    BigDecimal value =
        repository.findLatestValueForIsinOrGlobal(
            SEB_FUND_CONFIRMATION_NAV_TOLERANCE, ISIN, LocalDate.of(2025, 6, 15));

    assertThat(value).isEqualByComparingTo(new BigDecimal("0.004"));
  }

  @Test
  void findLatestValueForIsinOrGlobal_takesTheGlobalRowInForceBeforeTheIsinsRowTakesEffect() {
    insert(
        SEB_FUND_CONFIRMATION_NAV_TOLERANCE,
        null,
        new BigDecimal("0.004"),
        LocalDate.of(2025, 1, 1));
    insert(
        SEB_FUND_CONFIRMATION_NAV_TOLERANCE,
        null,
        new BigDecimal("0.006"),
        LocalDate.of(2025, 3, 1));
    insertForIsin(
        SEB_FUND_CONFIRMATION_NAV_TOLERANCE,
        ISIN,
        new BigDecimal("0.009"),
        LocalDate.of(2025, 7, 1));

    BigDecimal beforeTheIsinsRow =
        repository.findLatestValueForIsinOrGlobal(
            SEB_FUND_CONFIRMATION_NAV_TOLERANCE, ISIN, LocalDate.of(2025, 6, 30));
    BigDecimal fromTheIsinsRow =
        repository.findLatestValueForIsinOrGlobal(
            SEB_FUND_CONFIRMATION_NAV_TOLERANCE, ISIN, LocalDate.of(2025, 7, 1));

    assertThat(beforeTheIsinsRow).isEqualByComparingTo(new BigDecimal("0.006"));
    assertThat(fromTheIsinsRow).isEqualByComparingTo(new BigDecimal("0.009"));
  }

  @Test
  void findLatestValueForIsinOrGlobal_keepsTheIsinsRowWhenTheGlobalRowChangesLater() {
    insertForIsin(
        SEB_FUND_CONFIRMATION_NAV_TOLERANCE,
        ISIN,
        new BigDecimal("0.009"),
        LocalDate.of(2025, 1, 1));
    insert(
        SEB_FUND_CONFIRMATION_NAV_TOLERANCE,
        null,
        new BigDecimal("0.002"),
        LocalDate.of(2025, 3, 1));

    BigDecimal value =
        repository.findLatestValueForIsinOrGlobal(
            SEB_FUND_CONFIRMATION_NAV_TOLERANCE, ISIN, LocalDate.of(2025, 6, 15));

    assertThat(value).isEqualByComparingTo(new BigDecimal("0.009"));
  }

  @Test
  void findLatestValueForIsinOrGlobal_takesTheIsinsLatestRowInForce() {
    insertForIsin(
        SEB_FUND_CONFIRMATION_NAV_TOLERANCE,
        ISIN,
        new BigDecimal("0.009"),
        LocalDate.of(2025, 1, 1));
    insertForIsin(
        SEB_FUND_CONFIRMATION_NAV_TOLERANCE,
        ISIN,
        new BigDecimal("0.007"),
        LocalDate.of(2025, 3, 1));
    insertForIsin(
        SEB_FUND_CONFIRMATION_NAV_TOLERANCE,
        ISIN,
        new BigDecimal("0.005"),
        LocalDate.of(2025, 9, 1));

    BigDecimal value =
        repository.findLatestValueForIsinOrGlobal(
            SEB_FUND_CONFIRMATION_NAV_TOLERANCE, ISIN, LocalDate.of(2025, 6, 15));

    assertThat(value).isEqualByComparingTo(new BigDecimal("0.007"));
  }

  @Test
  void findLatestValueForIsinOrGlobal_ignoresFundScopedRows() {
    insert(
        SEB_FUND_CONFIRMATION_NAV_TOLERANCE,
        null,
        new BigDecimal("0.004"),
        LocalDate.of(2025, 1, 1));
    insert(
        SEB_FUND_CONFIRMATION_NAV_TOLERANCE,
        TUK75,
        new BigDecimal("0.009"),
        LocalDate.of(2025, 3, 1));
    insertForFundAndIsin(
        SEB_FUND_CONFIRMATION_NAV_TOLERANCE,
        TUK75,
        ISIN,
        new BigDecimal("0.008"),
        LocalDate.of(2025, 3, 1));

    BigDecimal value =
        repository.findLatestValueForIsinOrGlobal(
            SEB_FUND_CONFIRMATION_NAV_TOLERANCE, ISIN, LocalDate.of(2025, 6, 15));

    assertThat(value).isEqualByComparingTo(new BigDecimal("0.004"));
  }

  @Test
  void findLatestValueForIsinOrGlobal_throwsWhenNeitherTheIsinNorTheGlobalRowIsInForce() {
    insertForIsin(
        SEB_FUND_CONFIRMATION_NAV_TOLERANCE,
        ISIN,
        new BigDecimal("0.009"),
        LocalDate.of(2025, 7, 1));

    assertThatThrownBy(
            () ->
                repository.findLatestValueForIsinOrGlobal(
                    SEB_FUND_CONFIRMATION_NAV_TOLERANCE, ISIN, LocalDate.of(2025, 6, 15)))
        .isInstanceOf(IllegalStateException.class);
  }

  private void insert(
      InvestmentParameter parameter,
      TulevaFund fund,
      BigDecimal numericValue,
      LocalDate effectiveDate) {
    insertRow(parameter, fund == null ? null : fund.name(), null, numericValue, effectiveDate);
  }

  private void insertForIsin(
      InvestmentParameter parameter,
      String isin,
      BigDecimal numericValue,
      LocalDate effectiveDate) {
    insertRow(parameter, null, isin, numericValue, effectiveDate);
  }

  private void insertForFundAndIsin(
      InvestmentParameter parameter,
      TulevaFund fund,
      String isin,
      BigDecimal numericValue,
      LocalDate effectiveDate) {
    insertRow(parameter, fund.name(), isin, numericValue, effectiveDate);
  }

  private void insertRow(
      InvestmentParameter parameter,
      String fundCode,
      String isin,
      BigDecimal numericValue,
      LocalDate effectiveDate) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_parameter
              (effective_date, parameter_name, fund_code, isin, numeric_value)
            VALUES (:effectiveDate, :name, :fundCode, :isin, :numericValue)
            """)
        .param("effectiveDate", effectiveDate)
        .param("name", parameter.name())
        .param("fundCode", fundCode)
        .param("isin", isin)
        .param("numericValue", numericValue)
        .update();
  }
}
