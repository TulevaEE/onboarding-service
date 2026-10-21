package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.config.InvestmentParameter.TRACKING_BREACH_THRESHOLD;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.TRACKING_MAX_DAILY_RETURN;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.PriorityPriceProvider;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.JdbcFundValueRepository;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import ee.tuleva.onboarding.investment.check.tracking.TrackingDifferenceCalculator.PriceSnapshot;
import ee.tuleva.onboarding.investment.check.tracking.TrackingDifferenceCalculator.SecurityData;
import ee.tuleva.onboarding.investment.config.InvestmentParameter;
import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import ee.tuleva.onboarding.time.ClockConfig;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
@ComponentScan(basePackageClasses = InstrumentReferenceService.class)
@Import({
  BenchmarkCheckBuilder.class,
  BenchmarkLegResolver.class,
  ConsecutiveBreachTracker.class,
  TrackingDifferenceCalculator.class,
  InvestmentParameterRepository.class,
  JdbcFundValueRepository.class,
  PriorityPriceProvider.class,
  PublicHolidays.class,
  ClockConfig.class
})
class BenchmarkCheckBuilderIT {

  private static final LocalDate PREVIOUS_DATE = LocalDate.of(2026, 9, 28);
  private static final LocalDate CHECK_DATE = LocalDate.of(2026, 9, 29);
  private static final Instant INDEXED_AT = Instant.parse("2026-09-29T18:00:00Z");
  private static final String EURO_AGGREGATE_INDEX_FUND = "LU0826455353";
  private static final String GLOBAL_GOVERNMENT_INDEX_FUND = "LU0839970364";
  private static final String BOND_GLOBAL_PROXY_EODHD_KEY = "EUNA.XETRA";
  private static final String BOND_EURO_PROXY_EODHD_KEY = "EUN4.XETRA";
  private static final String BOND_EURO_PROXY_XETRA_KEY = "IE00B3DKXQ41.XETR";

  @Autowired BenchmarkCheckBuilder builder;
  @Autowired JdbcFundValueRepository fundValueRepository;
  @Autowired JdbcClient jdbcClient;

  @BeforeEach
  void storeTheTrackingParameters() {
    insert(TRACKING_MAX_DAILY_RETURN, new BigDecimal("0.5"));
    insert(TRACKING_BREACH_THRESHOLD, new BigDecimal("0.001"));
  }

  @Test
  void aBondHoldingIsMeasuredAgainstItsProxyWhenOnlyEodhdHasTheProxyPricesAsTheNavWouldPriceIt() {
    fundValueRepository.saveAll(
        List.of(
            proxyPrice(BOND_EURO_PROXY_EODHD_KEY, PREVIOUS_DATE, "100.00", "EODHD"),
            proxyPrice(BOND_EURO_PROXY_EODHD_KEY, CHECK_DATE, "101.00", "EODHD")));

    var result =
        builder.buildBenchmarkModelCheck(TUK00, CHECK_DATE, List.of(euroAggregateHolding()));

    assertThat(result)
        .hasValueSatisfying(
            check -> {
              assertThat(check.benchmarkGapIsins()).isEmpty();
              assertThat(check.benchmarkReturn()).isEqualByComparingTo("0.01");
            });
  }

  @Test
  void theProxyReturnTakesBothPricesFromOneFeedSoAnAdjustedCloseNeverMeetsARawOne() {
    fundValueRepository.saveAll(
        List.of(
            proxyPrice(BOND_EURO_PROXY_EODHD_KEY, PREVIOUS_DATE, "98.00", "EODHD"),
            proxyPrice(BOND_EURO_PROXY_XETRA_KEY, PREVIOUS_DATE, "100.00", "DEUTSCHE_BOERSE"),
            proxyPrice(BOND_EURO_PROXY_XETRA_KEY, CHECK_DATE, "100.00", "DEUTSCHE_BOERSE")));

    var result =
        builder.buildBenchmarkModelCheck(TUK00, CHECK_DATE, List.of(euroAggregateHolding()));

    assertThat(result)
        .hasValueSatisfying(check -> assertThat(check.benchmarkReturn()).isEqualByComparingTo("0"));
  }

  @Test
  void aBondHoldingWhoseProxyNoSourceHasPricedIsReportedAsABenchmarkGap() {
    fundValueRepository.saveAll(
        List.of(
            proxyPrice(BOND_GLOBAL_PROXY_EODHD_KEY, PREVIOUS_DATE, "50.00", "EODHD"),
            proxyPrice(BOND_GLOBAL_PROXY_EODHD_KEY, CHECK_DATE, "50.50", "EODHD")));

    var result =
        builder.buildBenchmarkModelCheck(
            TUK00,
            CHECK_DATE,
            List.of(
                holding(EURO_AGGREGATE_INDEX_FUND, new BigDecimal("0.50")),
                holding(GLOBAL_GOVERNMENT_INDEX_FUND, new BigDecimal("0.50"))));

    assertThat(result)
        .hasValueSatisfying(
            check -> {
              assertThat(check.benchmarkGapIsins()).containsExactly(EURO_AGGREGATE_INDEX_FUND);
              assertThat(check.benchmarkGapWeight()).isEqualByComparingTo("0.50");
            });
  }

  private static SecurityData euroAggregateHolding() {
    return holding(EURO_AGGREGATE_INDEX_FUND, BigDecimal.ONE);
  }

  private static SecurityData holding(String isin, BigDecimal weight) {
    return new SecurityData(
        isin,
        weight,
        weight,
        new PriceSnapshot(new BigDecimal("20.10"), CHECK_DATE),
        new PriceSnapshot(new BigDecimal("20.00"), PREVIOUS_DATE));
  }

  private static FundValue proxyPrice(String key, LocalDate date, String value, String provider) {
    return new FundValue(key, date, new BigDecimal(value), provider, INDEXED_AT);
  }

  private void insert(InvestmentParameter parameter, BigDecimal numericValue) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_parameter (effective_date, parameter_name, numeric_value)
            VALUES (:effectiveDate, :name, :numericValue)
            """)
        .param("effectiveDate", LocalDate.of(2026, 1, 1))
        .param("name", parameter.name())
        .param("numericValue", numericValue)
        .update();
  }
}
