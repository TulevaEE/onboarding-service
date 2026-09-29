package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.TrackingCheckType.BENCHMARK;
import static ee.tuleva.onboarding.investment.TrackingCheckType.BENCHMARK_MODEL;
import static ee.tuleva.onboarding.investment.TrackingCheckType.MODEL_PORTFOLIO;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.BENCHMARK_MODEL_BREACH_THRESHOLD;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.TRACKING_BREACH_THRESHOLD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.investment.config.InvestmentParameter;
import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
@Import({TrackingDifferenceCalculator.class, InvestmentParameterRepository.class})
class TrackingDifferenceCalculatorBreachThresholdIT {

  private static final LocalDate TRACKING_BREACH_THRESHOLD_EFFECTIVE_DATE =
      LocalDate.of(2026, 1, 1);
  private static final LocalDate BENCHMARK_MODEL_SEEDED_EFFECTIVE_DATE = LocalDate.of(2026, 9, 23);
  private static final LocalDate DAY_BEFORE_BENCHMARK_MODEL_HAD_ITS_OWN =
      BENCHMARK_MODEL_SEEDED_EFFECTIVE_DATE.minusDays(1);
  private static final LocalDate LATER_BENCHMARK_MODEL_EFFECTIVE_DATE = LocalDate.of(2026, 10, 1);

  private static final BigDecimal TRACKING_BREACH_THRESHOLD_VALUE = new BigDecimal("0.002");
  private static final BigDecimal SEEDED_BENCHMARK_MODEL_BREACH_THRESHOLD = new BigDecimal("0.001");
  private static final BigDecimal LATER_BENCHMARK_MODEL_BREACH_THRESHOLD = new BigDecimal("0.0015");

  @Autowired private JdbcClient jdbcClient;
  @Autowired private TrackingDifferenceCalculator calculator;

  @Test
  void benchmarkModelKeepsTheTrackingBreachThresholdOnCheckDatesBeforeItsOwnTookEffect() {
    insertTrackingBreachThreshold();

    assertThat(calculator.breachThreshold(BENCHMARK_MODEL, DAY_BEFORE_BENCHMARK_MODEL_HAD_ITS_OWN))
        .isEqualByComparingTo(TRACKING_BREACH_THRESHOLD_VALUE);
  }

  @Test
  void benchmarkModelReadsItsSeededThresholdFromTheDayItTookEffect() {
    insertTrackingBreachThreshold();

    assertThat(calculator.breachThreshold(BENCHMARK_MODEL, BENCHMARK_MODEL_SEEDED_EFFECTIVE_DATE))
        .isEqualByComparingTo(SEEDED_BENCHMARK_MODEL_BREACH_THRESHOLD);
  }

  @Test
  void aLaterBenchmarkModelThresholdGovernsFromItsOwnEffectiveDateOnly() {
    insertTrackingBreachThreshold();
    insert(
        BENCHMARK_MODEL_BREACH_THRESHOLD,
        LATER_BENCHMARK_MODEL_BREACH_THRESHOLD,
        LATER_BENCHMARK_MODEL_EFFECTIVE_DATE);

    assertThat(
            calculator.breachThreshold(
                BENCHMARK_MODEL, LATER_BENCHMARK_MODEL_EFFECTIVE_DATE.minusDays(1)))
        .isEqualByComparingTo(SEEDED_BENCHMARK_MODEL_BREACH_THRESHOLD);
    assertThat(calculator.breachThreshold(BENCHMARK_MODEL, LATER_BENCHMARK_MODEL_EFFECTIVE_DATE))
        .isEqualByComparingTo(LATER_BENCHMARK_MODEL_BREACH_THRESHOLD);
  }

  @Test
  void benchmarkModelFailsOnACheckDateWithNeitherItsOwnNorATrackingBreachThresholdInEffect() {
    assertThatThrownBy(
            () ->
                calculator.breachThreshold(BENCHMARK_MODEL, DAY_BEFORE_BENCHMARK_MODEL_HAD_ITS_OWN))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void modelPortfolioAndBenchmarkReadTheTrackingBreachThresholdEvenOnceBenchmarkModelHasItsOwn() {
    insertTrackingBreachThreshold();
    insert(
        BENCHMARK_MODEL_BREACH_THRESHOLD,
        LATER_BENCHMARK_MODEL_BREACH_THRESHOLD,
        LATER_BENCHMARK_MODEL_EFFECTIVE_DATE);

    assertThat(
            Stream.of(MODEL_PORTFOLIO, BENCHMARK)
                .map(
                    checkType ->
                        calculator.breachThreshold(
                            checkType, LATER_BENCHMARK_MODEL_EFFECTIVE_DATE)))
        .usingElementComparator(BigDecimal::compareTo)
        .containsExactly(TRACKING_BREACH_THRESHOLD_VALUE, TRACKING_BREACH_THRESHOLD_VALUE);
  }

  private void insertTrackingBreachThreshold() {
    insert(
        TRACKING_BREACH_THRESHOLD,
        TRACKING_BREACH_THRESHOLD_VALUE,
        TRACKING_BREACH_THRESHOLD_EFFECTIVE_DATE);
  }

  private void insert(
      InvestmentParameter parameter, BigDecimal numericValue, LocalDate effectiveDate) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_parameter (effective_date, parameter_name, numeric_value)
            VALUES (:effectiveDate, :name, :numericValue)
            """)
        .param("effectiveDate", effectiveDate)
        .param("name", parameter.name())
        .param("numericValue", numericValue)
        .update();
  }
}
