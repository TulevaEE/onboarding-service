package ee.tuleva.onboarding.comparisons.fundvalue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundTicker;
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
class PriorityPriceProviderTest {

  private static final LocalDate DATE = LocalDate.of(2026, 1, 15);
  private static final LocalDate OLDER_DATE = LocalDate.of(2026, 1, 12);
  private static final LocalDate STALE_DATE = LocalDate.of(2025, 12, 25);
  private static final Instant UPDATED_BEFORE = Instant.parse("2026-01-16T09:30:00Z");

  private static final String BLACKROCK_ISIN = "IE00BFG1TM61";
  private static final String ETF_ISIN = "IE00BFNM3G45";

  @Mock private FundValueProvider fundValueProvider;

  @InjectMocks private PriorityPriceProvider provider;

  @Test
  void resolve_withBlackrockAvailableOnTargetDate_returnsBlackrock() {
    FundTicker ticker = FundTicker.findByIsin(BLACKROCK_ISIN).orElseThrow();
    String blackrockKey = ticker.getBlackrockStorageKey().orElseThrow();
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
    FundTicker ticker = FundTicker.findByIsin(BLACKROCK_ISIN).orElseThrow();
    String blackrockKey = ticker.getBlackrockStorageKey().orElseThrow();
    String morningstarKey = ticker.getMorningstarStorageKey().orElseThrow();
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
  void resolve_blackrockOlderDate_eodhdCurrentDate_returnsEodhd() {
    FundTicker ticker = FundTicker.findByIsin(ETF_ISIN).orElseThrow();
    String eodhdTicker = ticker.getEodhdTicker();
    FundValue eodhdValue =
        new FundValue(eodhdTicker, DATE, new BigDecimal("100.00"), "EODHD", null);

    when(fundValueProvider.getLatestValue(eodhdTicker, DATE)).thenReturn(Optional.of(eodhdValue));

    Optional<FundValue> result = provider.resolve(ETF_ISIN, DATE);

    assertThat(result).isPresent();
    assertThat(result.get().date()).isEqualTo(DATE);
    assertThat(result.get().provider()).isEqualTo("EODHD");
  }

  @Test
  void resolve_allProvidersOnSameDate_returnsHighestPriority() {
    FundTicker ticker = FundTicker.findByIsin(BLACKROCK_ISIN).orElseThrow();
    String blackrockKey = ticker.getBlackrockStorageKey().orElseThrow();
    String morningstarKey = ticker.getMorningstarStorageKey().orElseThrow();
    String eodhdTicker = ticker.getEodhdTicker();
    String yahooTicker = ticker.getYahooTicker();

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
    FundTicker ticker = FundTicker.findByIsin(ETF_ISIN).orElseThrow();
    String eodhdTicker = ticker.getEodhdTicker();
    String yahooTicker = ticker.getYahooTicker();

    when(fundValueProvider.getLatestValue(eodhdTicker, DATE))
        .thenReturn(Optional.of(new FundValue(eodhdTicker, DATE, BigDecimal.ZERO, "EODHD", null)));
    when(fundValueProvider.getLatestValue(yahooTicker, DATE))
        .thenReturn(Optional.of(new FundValue(yahooTicker, DATE, BigDecimal.ZERO, "YAHOO", null)));

    Optional<FundValue> result = provider.resolve(ETF_ISIN, DATE);

    assertThat(result).isEmpty();
  }

  @Test
  void resolve_allPricesOlderThan14Days_returnsEmpty() {
    FundTicker ticker = FundTicker.findByIsin(ETF_ISIN).orElseThrow();
    String eodhdTicker = ticker.getEodhdTicker();
    String yahooTicker = ticker.getYahooTicker();

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
    FundTicker ticker = FundTicker.findByIsin(ETF_ISIN).orElseThrow();
    String eodhdTicker = ticker.getEodhdTicker();
    FundValue eodhdValue =
        new FundValue(eodhdTicker, DATE, new BigDecimal("100.00"), "EODHD", null);

    when(fundValueProvider.getLatestValue(eodhdTicker, DATE, UPDATED_BEFORE))
        .thenReturn(Optional.of(eodhdValue));

    Optional<FundValue> result = provider.resolve(ETF_ISIN, DATE, UPDATED_BEFORE);

    assertThat(result).isPresent();
    assertThat(result.get().provider()).isEqualTo("EODHD");
  }

  @Test
  void resolve_fundWithNoBlackrockMorningstar_triesEodhdAndYahoo() {
    FundTicker ticker = FundTicker.findByIsin(ETF_ISIN).orElseThrow();
    assertThat(ticker.getBlackrockStorageKey()).isEmpty();
    assertThat(ticker.getMorningstarStorageKey()).isEmpty();

    String yahooTicker = ticker.getYahooTicker();
    String eodhdTicker = ticker.getEodhdTicker();

    when(fundValueProvider.getLatestValue(eodhdTicker, DATE)).thenReturn(Optional.empty());
    when(fundValueProvider.getLatestValue(yahooTicker, DATE))
        .thenReturn(
            Optional.of(new FundValue(yahooTicker, DATE, new BigDecimal("99.00"), "YAHOO", null)));

    Optional<FundValue> result = provider.resolve(ETF_ISIN, DATE);

    assertThat(result).isPresent();
    assertThat(result.get().provider()).isEqualTo("YAHOO");
  }
}
