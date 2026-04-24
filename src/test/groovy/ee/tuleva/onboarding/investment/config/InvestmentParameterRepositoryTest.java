package ee.tuleva.onboarding.investment.config;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.TRACKING_BREACH_THRESHOLD;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.TRACKING_MAX_DAILY_RETURN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.fund.TulevaFund;
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

  @Test
  void findLatestValue_throwsWhenNoRowMatches() {
    assertThatThrownBy(
            () -> repository.findLatestValue(TRACKING_BREACH_THRESHOLD, LocalDate.of(2025, 6, 15)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No investment parameter found")
        .hasMessageContaining("parameter=TRACKING_BREACH_THRESHOLD")
        .hasMessageContaining("asOf=2025-06-15");
  }

  private void insert(
      InvestmentParameter parameter,
      TulevaFund fund,
      BigDecimal numericValue,
      LocalDate effectiveDate) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_parameter (effective_date, parameter_name, fund_code, numeric_value)
            VALUES (:effectiveDate, :name, :fundCode, :numericValue)
            """)
        .param("effectiveDate", effectiveDate)
        .param("name", parameter.name())
        .param("fundCode", fund == null ? null : fund.name())
        .param("numericValue", numericValue)
        .update();
  }
}
