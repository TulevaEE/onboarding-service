package ee.tuleva.onboarding.comparisons.fundvalue;

import static ee.tuleva.onboarding.comparisons.fundvalue.PriceSource.EODHD;
import static ee.tuleva.onboarding.comparisons.fundvalue.ValidationStatus.NO_PRICE_DATA;
import static ee.tuleva.onboarding.comparisons.fundvalue.ValidationStatus.OK;
import static ee.tuleva.onboarding.instrument.InstrumentReferenceFixture.instrument;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.instrument.InstrumentReference;
import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PositionPriceResolverTest {

  private static final String ISIN = "IE00BFNM3G45";
  private static final String EODHD_TICKER = "SGAS.XETRA";
  private static final LocalDate DATE = LocalDate.of(2025, 1, 10);
  private static final LocalDate OLDER_DATE = LocalDate.of(2025, 1, 7);

  private static final InstrumentReference INSTRUMENT =
      instrument(ISIN).eodhdTicker(EODHD_TICKER).yahooTicker("SGAS.DE").build();

  @Mock private PriorityPriceProvider priorityPriceProvider;

  @Mock private InstrumentReferenceService instrumentReferenceService;

  @InjectMocks private PositionPriceResolver resolver;

  private void givenKnownIsin() {
    when(instrumentReferenceService.findByIsin(ISIN)).thenReturn(Optional.of(INSTRUMENT));
  }

  @Test
  void resolve_withPriceAvailable_returnsOkStatus() {
    givenKnownIsin();
    BigDecimal price = new BigDecimal("100.00");
    FundValue fundValue = new FundValue(EODHD_TICKER, DATE, price, "EODHD", null);
    when(priorityPriceProvider.resolve(ISIN, DATE, null)).thenReturn(Optional.of(fundValue));

    Optional<ResolvedPrice> result = resolver.resolve(ISIN, DATE);

    assertThat(result).isPresent();
    ResolvedPrice resolved = result.get();
    assertThat(resolved.validationStatus()).isEqualTo(OK);
    assertThat(resolved.priceSource()).isEqualTo(EODHD);
    assertThat(resolved.usedPrice()).isEqualTo(price);
    assertThat(resolved.priceDate()).isEqualTo(DATE);
    assertThat(resolved.storageKey()).isEqualTo(EODHD_TICKER);
  }

  @Test
  void resolve_withNoPriceData_returnsNoPriceDataStatus() {
    givenKnownIsin();
    when(priorityPriceProvider.resolve(ISIN, DATE, null)).thenReturn(Optional.empty());

    Optional<ResolvedPrice> result = resolver.resolve(ISIN, DATE);

    assertThat(result).isPresent();
    ResolvedPrice resolved = result.get();
    assertThat(resolved.validationStatus()).isEqualTo(NO_PRICE_DATA);
    assertThat(resolved.priceSource()).isNull();
    assertThat(resolved.usedPrice()).isNull();
  }

  @Test
  void resolve_withOlderPriceDate_returnsPriceDateFromProvider() {
    givenKnownIsin();
    BigDecimal price = new BigDecimal("99.00");
    FundValue fundValue = new FundValue(EODHD_TICKER, OLDER_DATE, price, "EODHD", null);
    when(priorityPriceProvider.resolve(ISIN, DATE, null)).thenReturn(Optional.of(fundValue));

    Optional<ResolvedPrice> result = resolver.resolve(ISIN, DATE);

    assertThat(result).isPresent();
    assertThat(result.get().priceDate()).isEqualTo(OLDER_DATE);
  }

  @Test
  void resolve_withUpdatedBeforeCutoff_passesThrough() {
    givenKnownIsin();
    Instant cutoff = Instant.parse("2025-01-11T09:30:00Z");
    BigDecimal price = new BigDecimal("100.00");
    FundValue fundValue = new FundValue(EODHD_TICKER, DATE, price, "EODHD", null);

    when(priorityPriceProvider.resolve(ISIN, DATE, cutoff)).thenReturn(Optional.of(fundValue));

    Optional<ResolvedPrice> result = resolver.resolve(ISIN, DATE, cutoff);

    assertThat(result).isPresent();
    assertThat(result.get().validationStatus()).isEqualTo(OK);
    assertThat(result.get().usedPrice()).isEqualTo(price);
  }

  @Test
  void resolve_withUnknownIsin_returnsEmpty() {
    Optional<ResolvedPrice> result = resolver.resolve("UNKNOWN_ISIN", DATE);

    assertThat(result).isEmpty();
  }

  @Test
  void resolve_mapsProviderToPriceSource() {
    givenKnownIsin();
    FundValue fundValue = new FundValue("key", DATE, new BigDecimal("150.00"), "BLACKROCK", null);
    when(priorityPriceProvider.resolve(ISIN, DATE, null)).thenReturn(Optional.of(fundValue));

    Optional<ResolvedPrice> result = resolver.resolve(ISIN, DATE);

    assertThat(result).isPresent();
    assertThat(result.get().priceSource()).isEqualTo(PriceSource.BLACKROCK);
    assertThat(result.get().storageKey()).isEqualTo("key");
  }
}
