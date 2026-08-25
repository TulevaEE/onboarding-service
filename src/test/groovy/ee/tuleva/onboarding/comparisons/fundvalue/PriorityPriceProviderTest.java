package ee.tuleva.onboarding.comparisons.fundvalue;

import static ee.tuleva.onboarding.comparisons.fundvalue.PriceSource.DEUTSCHE_BOERSE;
import static ee.tuleva.onboarding.comparisons.fundvalue.PriceSource.EODHD;
import static ee.tuleva.onboarding.comparisons.fundvalue.PriceSource.YAHOO;
import static ee.tuleva.onboarding.instrument.InstrumentReferenceFixture.instrument;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.instrument.InstrumentReference;
import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PriorityPriceProviderTest {

  private static final LocalDate DATE = LocalDate.of(2026, 1, 15);
  private static final LocalDate OLDER_DATE = LocalDate.of(2026, 1, 12);
  private static final LocalDate STALE_DATE = LocalDate.of(2025, 12, 25);
  private static final Instant UPDATED_BEFORE = Instant.parse("2026-01-16T09:30:00Z");

  private static final String BLACKROCK_ISIN = "IE00BFG1TM61";
  private static final String ETF_ISIN = "IE00BFNM3G45";
  private static final String GAGH_ISIN = "LU1708330318";
  private static final String XWSC_ISIN = "IE000I9HGDZ3";

  private static final InstrumentReference BLACKROCK_FUND =
      instrument(BLACKROCK_ISIN)
          .displayName("iShares Developed World Screened Index Fund")
          .yahooTicker("0P000152G5.F")
          .eodhdTicker("IE00BFG1TM61.EUFUND")
          .blackrockProductId("270890")
          .morningstarId("0P000152G5")
          .build();

  private static final InstrumentReference XETRA_ETF =
      instrument(ETF_ISIN)
          .displayName("iShares MSCI USA Screened UCITS ETF")
          .yahooTicker("SGAS.DE")
          .eodhdTicker("SGAS.XETRA")
          .build();

  private static final InstrumentReference NO_LONGER_LISTED_ON_EODHD_ETF =
      instrument(XWSC_ISIN)
          .displayName("Xtrackers MSCI World Screened UCITS ETF 1C")
          .yahooTicker("XWSC.DE")
          .eodhdTicker("XWSC.XETRA")
          .eodhdListed(false)
          .build();

  private static final InstrumentReference EURONEXT_ETF =
      instrument(GAGH_ISIN)
          .displayName("Amundi Core Global Aggregate Bond UCITS ETF EUR Hedged")
          .yahooTicker("GAGH.PA")
          .eodhdTicker("GAGH.PA.EODHD")
          .build();

  @Mock private FundValueProvider fundValueProvider;

  @Mock private InstrumentReferenceService instrumentReferenceService;

  @InjectMocks private PriorityPriceProvider provider;

  private InstrumentReference givenKnown(InstrumentReference instrument) {
    when(instrumentReferenceService.findByIsin(instrument.getIsin()))
        .thenReturn(Optional.of(instrument));
    return instrument;
  }

  @Test
  void resolve_withBlackrockAvailableOnTargetDate_returnsBlackrock() {
    InstrumentReference instrument = givenKnown(BLACKROCK_FUND);
    String blackrockKey = instrument.getBlackrockStorageKey().orElseThrow();
    FundValue blackrockValue =
        new FundValue(blackrockKey, DATE, new BigDecimal("150.00"), "BLACKROCK", null);

    when(fundValueProvider.getLatestValue(blackrockKey, DATE))
        .thenReturn(Optional.of(blackrockValue));

    Optional<FundValue> result = provider.resolve(BLACKROCK_ISIN, DATE);

    assertThat(result).isPresent();
    assertThat(result.get().provider()).isEqualTo("BLACKROCK");
    assertThat(result.get().value()).isEqualByComparingTo(new BigDecimal("150.00"));
    assertThat(result.get().date()).isEqualTo(DATE);
  }

  @Test
  void resolve_withoutBlackrock_returnsMorningstar() {
    InstrumentReference instrument = givenKnown(BLACKROCK_FUND);
    String blackrockKey = instrument.getBlackrockStorageKey().orElseThrow();
    String morningstarKey = instrument.getMorningstarStorageKey().orElseThrow();
    FundValue morningstarValue =
        new FundValue(morningstarKey, DATE, new BigDecimal("149.50"), "MORNINGSTAR", null);

    when(fundValueProvider.getLatestValue(blackrockKey, DATE)).thenReturn(Optional.empty());
    when(fundValueProvider.getLatestValue(morningstarKey, DATE))
        .thenReturn(Optional.of(morningstarValue));

    Optional<FundValue> result = provider.resolve(BLACKROCK_ISIN, DATE);

    assertThat(result).isPresent();
    assertThat(result.get().provider()).isEqualTo("MORNINGSTAR");
  }

  @Test
  void resolve_morningstarSameDateAsEodhd_prefersMorningstar() {
    InstrumentReference instrument = givenKnown(BLACKROCK_FUND);
    String blackrockKey = instrument.getBlackrockStorageKey().orElseThrow();
    String morningstarKey = instrument.getMorningstarStorageKey().orElseThrow();
    String eodhdTicker = instrument.getEodhdTicker();

    when(fundValueProvider.getLatestValue(blackrockKey, DATE)).thenReturn(Optional.empty());
    when(fundValueProvider.getLatestValue(morningstarKey, DATE))
        .thenReturn(
            Optional.of(
                new FundValue(
                    morningstarKey, DATE, new BigDecimal("149.50"), "MORNINGSTAR", null)));
    when(fundValueProvider.getLatestValue(eodhdTicker, DATE))
        .thenReturn(
            Optional.of(new FundValue(eodhdTicker, DATE, new BigDecimal("149.80"), "EODHD", null)));

    Optional<FundValue> result = provider.resolve(BLACKROCK_ISIN, DATE);

    assertThat(result).isPresent();
    assertThat(result.get().provider()).isEqualTo("MORNINGSTAR");
  }

  @Test
  void resolve_blackrockOlderDate_eodhdCurrentDate_returnsEodhd() {
    InstrumentReference instrument = givenKnown(XETRA_ETF);
    String xetraKey = instrument.getXetraStorageKey().orElseThrow();
    String eodhdTicker = instrument.getEodhdTicker();
    FundValue eodhdValue =
        new FundValue(eodhdTicker, DATE, new BigDecimal("100.00"), "EODHD", null);

    when(fundValueProvider.getLatestValue(xetraKey, DATE)).thenReturn(Optional.empty());
    when(fundValueProvider.getLatestValue(eodhdTicker, DATE)).thenReturn(Optional.of(eodhdValue));

    Optional<FundValue> result = provider.resolve(ETF_ISIN, DATE);

    assertThat(result).isPresent();
    assertThat(result.get().date()).isEqualTo(DATE);
    assertThat(result.get().provider()).isEqualTo("EODHD");
  }

  @Test
  void resolve_allProvidersOnSameDate_returnsHighestPriority() {
    InstrumentReference instrument = givenKnown(BLACKROCK_FUND);
    String blackrockKey = instrument.getBlackrockStorageKey().orElseThrow();
    String morningstarKey = instrument.getMorningstarStorageKey().orElseThrow();
    String eodhdTicker = instrument.getEodhdTicker();
    String yahooTicker = instrument.getYahooTicker();

    when(fundValueProvider.getLatestValue(blackrockKey, DATE))
        .thenReturn(
            Optional.of(
                new FundValue(blackrockKey, DATE, new BigDecimal("150.00"), "BLACKROCK", null)));
    when(fundValueProvider.getLatestValue(morningstarKey, DATE))
        .thenReturn(
            Optional.of(
                new FundValue(
                    morningstarKey, DATE, new BigDecimal("149.50"), "MORNINGSTAR", null)));
    when(fundValueProvider.getLatestValue(eodhdTicker, DATE))
        .thenReturn(
            Optional.of(new FundValue(eodhdTicker, DATE, new BigDecimal("149.80"), "EODHD", null)));
    when(fundValueProvider.getLatestValue(yahooTicker, DATE))
        .thenReturn(
            Optional.of(new FundValue(yahooTicker, DATE, new BigDecimal("149.70"), "YAHOO", null)));

    Optional<FundValue> result = provider.resolve(BLACKROCK_ISIN, DATE);

    assertThat(result).isPresent();
    assertThat(result.get().provider()).isEqualTo("BLACKROCK");
  }

  @Test
  void resolve_allProvidersReturnZero_returnsEmpty() {
    InstrumentReference instrument = givenKnown(XETRA_ETF);
    String xetraKey = instrument.getXetraStorageKey().orElseThrow();
    String eodhdTicker = instrument.getEodhdTicker();
    String yahooTicker = instrument.getYahooTicker();

    when(fundValueProvider.getLatestValue(xetraKey, DATE))
        .thenReturn(
            Optional.of(new FundValue(xetraKey, DATE, BigDecimal.ZERO, "DEUTSCHE_BOERSE", null)));
    when(fundValueProvider.getLatestValue(eodhdTicker, DATE))
        .thenReturn(Optional.of(new FundValue(eodhdTicker, DATE, BigDecimal.ZERO, "EODHD", null)));
    when(fundValueProvider.getLatestValue(yahooTicker, DATE))
        .thenReturn(Optional.of(new FundValue(yahooTicker, DATE, BigDecimal.ZERO, "YAHOO", null)));

    Optional<FundValue> result = provider.resolve(ETF_ISIN, DATE);

    assertThat(result).isEmpty();
  }

  @Test
  void resolve_allPricesOlderThan14Days_returnsEmpty() {
    InstrumentReference instrument = givenKnown(XETRA_ETF);
    String xetraKey = instrument.getXetraStorageKey().orElseThrow();
    String eodhdTicker = instrument.getEodhdTicker();
    String yahooTicker = instrument.getYahooTicker();

    when(fundValueProvider.getLatestValue(xetraKey, DATE))
        .thenReturn(
            Optional.of(
                new FundValue(
                    xetraKey, STALE_DATE, new BigDecimal("100.00"), "DEUTSCHE_BOERSE", null)));
    when(fundValueProvider.getLatestValue(eodhdTicker, DATE))
        .thenReturn(
            Optional.of(
                new FundValue(eodhdTicker, STALE_DATE, new BigDecimal("100.00"), "EODHD", null)));
    when(fundValueProvider.getLatestValue(yahooTicker, DATE))
        .thenReturn(
            Optional.of(
                new FundValue(yahooTicker, STALE_DATE, new BigDecimal("100.00"), "YAHOO", null)));

    Optional<FundValue> result = provider.resolve(ETF_ISIN, DATE);

    assertThat(result).isEmpty();
  }

  @Test
  void resolve_unknownIsin_returnsEmpty() {
    Optional<FundValue> result = provider.resolve("UNKNOWN_ISIN", DATE);

    assertThat(result).isEmpty();
  }

  @Test
  void resolve_withUpdatedBeforeCutoff_passesThrough() {
    InstrumentReference instrument = givenKnown(XETRA_ETF);
    String xetraKey = instrument.getXetraStorageKey().orElseThrow();
    String eodhdTicker = instrument.getEodhdTicker();
    FundValue eodhdValue =
        new FundValue(eodhdTicker, DATE, new BigDecimal("100.00"), "EODHD", null);

    when(fundValueProvider.getLatestValue(xetraKey, DATE, UPDATED_BEFORE))
        .thenReturn(Optional.empty());
    when(fundValueProvider.getLatestValue(eodhdTicker, DATE, UPDATED_BEFORE))
        .thenReturn(Optional.of(eodhdValue));

    Optional<FundValue> result = provider.resolve(ETF_ISIN, DATE, UPDATED_BEFORE);

    assertThat(result).isPresent();
    assertThat(result.get().provider()).isEqualTo("EODHD");
  }

  @Test
  void resolve_fundWithNoBlackrockMorningstar_triesEodhdAndYahoo() {
    InstrumentReference instrument = givenKnown(XETRA_ETF);
    assertThat(instrument.getBlackrockStorageKey()).isEmpty();
    assertThat(instrument.getMorningstarStorageKey()).isEmpty();

    String xetraKey = instrument.getXetraStorageKey().orElseThrow();
    String yahooTicker = instrument.getYahooTicker();
    String eodhdTicker = instrument.getEodhdTicker();

    when(fundValueProvider.getLatestValue(xetraKey, DATE)).thenReturn(Optional.empty());
    when(fundValueProvider.getLatestValue(eodhdTicker, DATE)).thenReturn(Optional.empty());
    when(fundValueProvider.getLatestValue(yahooTicker, DATE))
        .thenReturn(
            Optional.of(new FundValue(yahooTicker, DATE, new BigDecimal("99.00"), "YAHOO", null)));

    Optional<FundValue> result = provider.resolve(ETF_ISIN, DATE);

    assertThat(result).isPresent();
    assertThat(result.get().provider()).isEqualTo("YAHOO");
  }

  @Test
  void resolve_eodhdSameDateAsXetra_prefersEodhd() {
    InstrumentReference instrument = givenKnown(XETRA_ETF);
    String xetraKey = instrument.getXetraStorageKey().orElseThrow();
    String eodhdTicker = instrument.getEodhdTicker();

    when(fundValueProvider.getLatestValue(xetraKey, DATE))
        .thenReturn(
            Optional.of(
                new FundValue(xetraKey, DATE, new BigDecimal("100.50"), "DEUTSCHE_BOERSE", null)));
    when(fundValueProvider.getLatestValue(eodhdTicker, DATE))
        .thenReturn(
            Optional.of(new FundValue(eodhdTicker, DATE, new BigDecimal("100.40"), "EODHD", null)));

    Optional<FundValue> result = provider.resolve(ETF_ISIN, DATE);

    assertThat(result).isPresent();
    assertThat(result.get().provider()).isEqualTo("EODHD");
  }

  @Test
  void resolve_eodhdMissing_fallsBackToXetra() {
    InstrumentReference instrument = givenKnown(XETRA_ETF);
    String xetraKey = instrument.getXetraStorageKey().orElseThrow();
    String eodhdTicker = instrument.getEodhdTicker();

    when(fundValueProvider.getLatestValue(xetraKey, DATE))
        .thenReturn(
            Optional.of(
                new FundValue(xetraKey, DATE, new BigDecimal("100.50"), "DEUTSCHE_BOERSE", null)));
    when(fundValueProvider.getLatestValue(eodhdTicker, DATE)).thenReturn(Optional.empty());

    Optional<FundValue> result = provider.resolve(ETF_ISIN, DATE);

    assertThat(result).isPresent();
    assertThat(result.get().provider()).isEqualTo("DEUTSCHE_BOERSE");
  }

  @Test
  void resolve_eodhdSameDateAsEuronext_prefersEodhd() {
    InstrumentReference instrument = givenKnown(EURONEXT_ETF);
    String euronextKey = instrument.getEuronextParisStorageKey().orElseThrow();
    String eodhdTicker = instrument.getEodhdTicker();

    when(fundValueProvider.getLatestValue(euronextKey, DATE))
        .thenReturn(
            Optional.of(
                new FundValue(euronextKey, DATE, new BigDecimal("50.25"), "EURONEXT", null)));
    when(fundValueProvider.getLatestValue(eodhdTicker, DATE))
        .thenReturn(
            Optional.of(new FundValue(eodhdTicker, DATE, new BigDecimal("50.24"), "EODHD", null)));

    Optional<FundValue> result = provider.resolve(GAGH_ISIN, DATE);

    assertThat(result).isPresent();
    assertThat(result.get().provider()).isEqualTo("EODHD");
  }

  @Test
  void resolve_euronextParisForGagh() {
    InstrumentReference instrument = givenKnown(EURONEXT_ETF);
    assertThat(instrument.getXetraStorageKey()).isEmpty();

    String euronextKey = instrument.getEuronextParisStorageKey().orElseThrow();
    String eodhdTicker = instrument.getEodhdTicker();
    String yahooTicker = instrument.getYahooTicker();

    when(fundValueProvider.getLatestValue(euronextKey, DATE))
        .thenReturn(
            Optional.of(
                new FundValue(euronextKey, DATE, new BigDecimal("50.25"), "EURONEXT", null)));
    when(fundValueProvider.getLatestValue(eodhdTicker, DATE)).thenReturn(Optional.empty());
    when(fundValueProvider.getLatestValue(yahooTicker, DATE))
        .thenReturn(
            Optional.of(new FundValue(yahooTicker, DATE, new BigDecimal("50.20"), "YAHOO", null)));

    Optional<FundValue> result = provider.resolve(GAGH_ISIN, DATE);

    assertThat(result).isPresent();
    assertThat(result.get().provider()).isEqualTo("EURONEXT");
  }

  @Test
  void priceFeeds_forEodhdListedInstrument_yieldEodhdKeyAheadOfTheExchange() {
    assertThat(sourcesWithStorageKey(XETRA_ETF)).containsExactly(EODHD, DEUTSCHE_BOERSE, YAHOO);
  }

  @Test
  void priceFeeds_forInstrumentNoLongerListedOnEodhd_yieldNoEodhdKey() {
    assertThat(NO_LONGER_LISTED_ON_EODHD_ETF.getEodhdTicker()).isNotNull();

    assertThat(sourcesWithStorageKey(NO_LONGER_LISTED_ON_EODHD_ETF))
        .containsExactly(DEUTSCHE_BOERSE, YAHOO);
  }

  @Test
  void resolve_instrumentNoLongerListedOnEodhd_fallsBackToTheExchange() {
    InstrumentReference instrument = givenKnown(NO_LONGER_LISTED_ON_EODHD_ETF);
    String xetraKey = instrument.getXetraStorageKey().orElseThrow();
    String yahooTicker = instrument.getYahooTicker();

    when(fundValueProvider.getLatestValue(xetraKey, DATE))
        .thenReturn(
            Optional.of(
                new FundValue(xetraKey, DATE, new BigDecimal("100.50"), "DEUTSCHE_BOERSE", null)));
    when(fundValueProvider.getLatestValue(yahooTicker, DATE))
        .thenReturn(
            Optional.of(new FundValue(yahooTicker, DATE, new BigDecimal("100.40"), "YAHOO", null)));

    Optional<FundValue> result = provider.resolve(XWSC_ISIN, DATE);

    assertThat(result).isPresent();
    assertThat(result.get().provider()).isEqualTo("DEUTSCHE_BOERSE");
    verify(fundValueProvider, never()).getLatestValue(eq("XWSC.XETRA"), any(LocalDate.class));
  }

  private List<PriceSource> sourcesWithStorageKey(InstrumentReference instrument) {
    return PriorityPriceProvider.priceFeeds().stream()
        .filter(feed -> feed.storageKey().apply(instrument).isPresent())
        .map(PriorityPriceProvider.PriceFeed::source)
        .toList();
  }
}
