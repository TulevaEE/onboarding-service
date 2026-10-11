package ee.tuleva.onboarding.comparisons.fundvalue;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.comparisons.fundvalue.persistence.JdbcFundValueRepository;
import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import ee.tuleva.onboarding.time.ClockConfig;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

@DataJpaTest
@ComponentScan(basePackageClasses = InstrumentReferenceService.class)
@Import({JdbcFundValueRepository.class, PriorityPriceProvider.class, ClockConfig.class})
class PriorityPriceProviderIntegrationTest {

  private static final String WLSC_ISIN = "IE000QWCYQT0";
  private static final LocalDate PRICE_DATE = LocalDate.of(2026, 9, 28);
  private static final Instant INDEXED_AT = Instant.parse("2026-09-29T04:00:20Z");
  private static final Instant PRICE_CUTOFF = Instant.parse("2026-09-29T08:05:00Z");

  @Autowired JdbcFundValueRepository fundValueRepository;
  @Autowired PriorityPriceProvider priorityPriceProvider;

  @Test
  void resolvesAParisEtfToTheEuronextOfficialCloseWhenEodhdServedTheLastTrade() {
    fundValueRepository.saveAll(
        List.of(
            new FundValue(
                "WLSC.PA.EODHD", PRICE_DATE, new BigDecimal("5.081"), "EODHD", INDEXED_AT),
            new FundValue(
                WLSC_ISIN + ".XPAR", PRICE_DATE, new BigDecimal("5.080"), "EURONEXT", INDEXED_AT)));

    var resolved = priorityPriceProvider.resolve(WLSC_ISIN, PRICE_DATE, PRICE_CUTOFF);

    assertThat(resolved)
        .hasValueSatisfying(
            price -> {
              assertThat(price.provider()).isEqualTo("EURONEXT");
              assertThat(price.value()).isEqualByComparingTo("5.080");
              assertThat(price.date()).isEqualTo(PRICE_DATE);
            });
  }

  @Test
  void fallsBackToEodhdWhenEuronextHasNoCloseForTheDate() {
    fundValueRepository.saveAll(
        List.of(
            new FundValue(
                WLSC_ISIN + ".XPAR",
                PRICE_DATE.minusDays(3),
                new BigDecimal("5.094"),
                "EURONEXT",
                INDEXED_AT),
            new FundValue(
                "WLSC.PA.EODHD", PRICE_DATE, new BigDecimal("5.080"), "EODHD", INDEXED_AT)));

    var resolved = priorityPriceProvider.resolve(WLSC_ISIN, PRICE_DATE, PRICE_CUTOFF);

    assertThat(resolved)
        .hasValueSatisfying(
            price -> {
              assertThat(price.provider()).isEqualTo("EODHD");
              assertThat(price.value()).isEqualByComparingTo("5.080");
              assertThat(price.date()).isEqualTo(PRICE_DATE);
            });
  }
}
