package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.instrument.InstrumentReferenceFixture.instrument;
import static ee.tuleva.onboarding.instrument.InstrumentReferenceServiceFixture.instrumentReferenceService;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.TRACKING_MAX_DAILY_RETURN;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.comparisons.fundvalue.PriorityPriceProvider;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.JdbcFundValueRepository;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.instrument.BenchmarkCategoryProxy;
import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import ee.tuleva.onboarding.investment.check.tracking.BenchmarkModelCheck.Measured;
import ee.tuleva.onboarding.investment.check.tracking.TrackingDifferenceCalculator.PriceSnapshot;
import ee.tuleva.onboarding.investment.check.tracking.TrackingDifferenceCalculator.SecurityData;
import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
@Import({
  BenchmarkCheckBuilder.class,
  BenchmarkPrices.class,
  TrackingDifferenceCalculator.class,
  InvestmentParameterRepository.class,
  JdbcFundValueRepository.class,
  PriorityPriceProvider.class,
  BenchmarkLegResolver.class,
  ConsecutiveBreachTracker.class,
  PublicHolidays.class,
  BenchmarkCheckBuilderIT.Instruments.class
})
class BenchmarkCheckBuilderIT {

  private static final LocalDate PREVIOUS_DATE = LocalDate.of(2026, 11, 10);
  private static final LocalDate CHECK_DATE = LocalDate.of(2026, 11, 11);
  private static final String DEVELOPED_MARKETS_ETF_ISIN = "IE000I9HGDZ3";
  private static final String DEVELOPED_MARKETS_PROXY_ISIN = "IE00B4L5Y983";
  private static final String PROXY_EODHD_KEY = "EUNL.XETRA";
  private static final String PROXY_DEUTSCHE_BOERSE_KEY = "IE00B4L5Y983.XETR";

  @TestConfiguration
  static class Instruments {
    @Bean
    InstrumentReferenceService anEtfAndItsDevelopedMarketsProxy() {
      return instrumentReferenceService(
          List.of(
              instrument(DEVELOPED_MARKETS_ETF_ISIN)
                  .eodhdTicker("XWSC.XETRA")
                  .benchmarkCategory("EQUITY_DM")
                  .build(),
              instrument(DEVELOPED_MARKETS_PROXY_ISIN).eodhdTicker(PROXY_EODHD_KEY).build()),
          List.of(
              new BenchmarkCategoryProxy(
                  1L, "EQUITY_DM", DEVELOPED_MARKETS_PROXY_ISIN, null, "MSCI_WORLD")));
    }

    @Bean
    Clock clock() {
      return Clock.fixed(Instant.parse("2026-11-12T09:00:00Z"), ZoneOffset.UTC);
    }
  }

  @Autowired BenchmarkCheckBuilder builder;
  @Autowired JdbcClient jdbcClient;

  @BeforeEach
  void storeTheMaxDailyReturn() {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_parameter (effective_date, parameter_name, numeric_value)
            VALUES (:effectiveDate, :name, :numericValue)
            """)
        .param("effectiveDate", LocalDate.of(2026, 1, 1))
        .param("name", TRACKING_MAX_DAILY_RETURN.name())
        .param("numericValue", new BigDecimal("0.5"))
        .update();
  }

  @Test
  void anEtfHoldingTakesItsProxysEodhdCloseWhenDeutscheBoerseHasNoRowForTheDay() {
    storePrice(PROXY_DEUTSCHE_BOERSE_KEY, PREVIOUS_DATE, "128.65", "DEUTSCHE_BOERSE");
    storePrice(PROXY_EODHD_KEY, PREVIOUS_DATE, "128.65", "EODHD");
    storePrice(PROXY_EODHD_KEY, CHECK_DATE, "130.955", "EODHD");

    var check = builder.buildBenchmarkModelCheck(TUK75, CHECK_DATE, List.of(etfHolding()));

    assertThat(check).isInstanceOf(Measured.class);
    assertThat(((Measured) check).result().securityAttributions())
        .singleElement()
        .satisfies(
            attribution -> {
              assertThat(attribution.benchmarkReturn()).isEqualByComparingTo("0.017917");
              assertThat(attribution.staleBenchmarkPrices()).isEmpty();
            });
  }

  @Test
  void anEtfHoldingWhoseProxyHasNoCloseForTheDayInAnySourceIsFlaggedWithTheDateItWasMeasuredOn() {
    storePrice(PROXY_DEUTSCHE_BOERSE_KEY, PREVIOUS_DATE, "128.65", "DEUTSCHE_BOERSE");
    storePrice(PROXY_EODHD_KEY, PREVIOUS_DATE, "128.65", "EODHD");

    var check = builder.buildBenchmarkModelCheck(TUK75, CHECK_DATE, List.of(etfHolding()));

    assertThat(check).isInstanceOf(Measured.class);
    assertThat(((Measured) check).result().securityAttributions())
        .singleElement()
        .extracting(SecurityAttribution::staleBenchmarkPrices)
        .isEqualTo(
            List.of(
                new StaleBenchmarkPrice(DEVELOPED_MARKETS_PROXY_ISIN, CHECK_DATE, PREVIOUS_DATE)));
  }

  private static SecurityData etfHolding() {
    return new SecurityData(
        DEVELOPED_MARKETS_ETF_ISIN,
        BigDecimal.ONE,
        BigDecimal.ONE,
        new PriceSnapshot(new BigDecimal("10.40"), CHECK_DATE),
        new PriceSnapshot(new BigDecimal("10.182"), PREVIOUS_DATE));
  }

  private void storePrice(String key, LocalDate date, String value, String provider) {
    jdbcClient
        .sql(
            """
            INSERT INTO index_values (key, date, value, provider, updated_at)
            VALUES (:key, :date, :value, :provider, :updatedAt)
            """)
        .param("key", key)
        .param("date", date)
        .param("value", new BigDecimal(value))
        .param("provider", provider)
        .param("updatedAt", Instant.parse("2026-11-12T05:00:00Z"))
        .update();
  }
}
