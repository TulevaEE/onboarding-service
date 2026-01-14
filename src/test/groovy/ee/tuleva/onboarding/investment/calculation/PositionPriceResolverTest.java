package ee.tuleva.onboarding.investment.calculation;

import static ee.tuleva.onboarding.investment.calculation.PriceSource.EODHD;
import static ee.tuleva.onboarding.investment.calculation.PriceSource.YAHOO;
import static ee.tuleva.onboarding.investment.calculation.ValidationStatus.EODHD_MISSING;
import static ee.tuleva.onboarding.investment.calculation.ValidationStatus.NO_PRICE_DATA;
import static ee.tuleva.onboarding.investment.calculation.ValidationStatus.OK;
import static ee.tuleva.onboarding.investment.calculation.ValidationStatus.PRICE_DISCREPANCY;
import static ee.tuleva.onboarding.investment.calculation.ValidationStatus.YAHOO_MISSING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueProvider;
import java.math.BigDecimal;
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
  private static final String YAHOO_TICKER = "SGAS.DE";
  private static final LocalDate DATE = LocalDate.of(2025, 1, 10);
  private static final LocalDate OLDER_DATE = LocalDate.of(2025, 1, 7);

  @Mock private FundValueProvider fundValueProvider;

  @InjectMocks private PositionPriceResolver resolver;

  @Test
  void resolve_withBothPricesWithinThreshold_returnsOkStatus() {
    BigDecimal eodhdPrice = new BigDecimal("100.00");
    BigDecimal yahooPrice = new BigDecimal("100.05");
    mockPricesForDate(eodhdPrice, yahooPrice);

    Optional<ResolvedPrice> result = resolver.resolve(ISIN, DATE);

    assertThat(result).isPresent();
    ResolvedPrice resolved = result.get();
    assertThat(resolved.validationStatus()).isEqualTo(OK);
    assertThat(resolved.priceSource()).isEqualTo(EODHD);
    assertThat(resolved.usedPrice()).isEqualTo(eodhdPrice);
    assertThat(resolved.eodhdPrice()).isEqualTo(eodhdPrice);
    assertThat(resolved.yahooPrice()).isEqualTo(yahooPrice);
    assertThat(resolved.priceDate()).isEqualTo(DATE);
  }

  @Test
  void resolve_withPriceDiscrepancyAboveThreshold_returnsPriceDiscrepancyStatus() {
    BigDecimal eodhdPrice = new BigDecimal("100.00");
    BigDecimal yahooPrice = new BigDecimal("101.00");
    mockPricesForDate(eodhdPrice, yahooPrice);

    Optional<ResolvedPrice> result = resolver.resolve(ISIN, DATE);

    assertThat(result).isPresent();
    ResolvedPrice resolved = result.get();
    assertThat(resolved.validationStatus()).isEqualTo(PRICE_DISCREPANCY);
    assertThat(resolved.priceSource()).isEqualTo(EODHD);
    assertThat(resolved.usedPrice()).isEqualTo(eodhdPrice);
    assertThat(resolved.discrepancyPercent()).isGreaterThan(new BigDecimal("0.1"));
  }

  @Test
  void resolve_withOnlyEodhdPrice_returnsYahooMissingStatus() {
    BigDecimal eodhdPrice = new BigDecimal("100.00");
    mockPricesForDate(eodhdPrice, null);

    Optional<ResolvedPrice> result = resolver.resolve(ISIN, DATE);

    assertThat(result).isPresent();
    ResolvedPrice resolved = result.get();
    assertThat(resolved.validationStatus()).isEqualTo(YAHOO_MISSING);
    assertThat(resolved.priceSource()).isEqualTo(EODHD);
    assertThat(resolved.usedPrice()).isEqualTo(eodhdPrice);
    assertThat(resolved.yahooPrice()).isNull();
  }

  @Test
  void resolve_withOnlyYahooPrice_returnsEodhdMissingStatus() {
    BigDecimal yahooPrice = new BigDecimal("100.00");
    mockPricesForDate(null, yahooPrice);

    Optional<ResolvedPrice> result = resolver.resolve(ISIN, DATE);

    assertThat(result).isPresent();
    ResolvedPrice resolved = result.get();
    assertThat(resolved.validationStatus()).isEqualTo(EODHD_MISSING);
    assertThat(resolved.priceSource()).isEqualTo(YAHOO);
    assertThat(resolved.usedPrice()).isEqualTo(yahooPrice);
    assertThat(resolved.eodhdPrice()).isNull();
  }

  @Test
  void resolve_withNoPricesForDate_fallsBackToLatestPricesWithOlderDate() {
    BigDecimal latestEodhdPrice = new BigDecimal("99.00");
    BigDecimal latestYahooPrice = new BigDecimal("99.05");
    mockPricesForDate(null, null);
    mockLatestPrices(latestEodhdPrice, latestYahooPrice);

    Optional<ResolvedPrice> result = resolver.resolve(ISIN, DATE);

    assertThat(result).isPresent();
    ResolvedPrice resolved = result.get();
    assertThat(resolved.validationStatus()).isEqualTo(OK);
    assertThat(resolved.priceSource()).isEqualTo(EODHD);
    assertThat(resolved.usedPrice()).isEqualTo(latestEodhdPrice);
    assertThat(resolved.priceDate()).isEqualTo(OLDER_DATE);
  }

  @Test
  void resolve_withNoPricesAndNoLatestPrices_returnsNoPriceDataStatus() {
    mockPricesForDate(null, null);
    mockLatestPrices(null, null);

    Optional<ResolvedPrice> result = resolver.resolve(ISIN, DATE);

    assertThat(result).isPresent();
    ResolvedPrice resolved = result.get();
    assertThat(resolved.validationStatus()).isEqualTo(NO_PRICE_DATA);
    assertThat(resolved.priceSource()).isNull();
    assertThat(resolved.usedPrice()).isNull();
  }

  @Test
  void resolve_withZeroPricesForDate_fallsBackToLatestPrices() {
    BigDecimal latestEodhdPrice = new BigDecimal("99.00");
    mockPricesForDate(BigDecimal.ZERO, BigDecimal.ZERO);
    mockLatestPrices(latestEodhdPrice, null);

    Optional<ResolvedPrice> result = resolver.resolve(ISIN, DATE);

    assertThat(result).isPresent();
    ResolvedPrice resolved = result.get();
    assertThat(resolved.validationStatus()).isEqualTo(YAHOO_MISSING);
    assertThat(resolved.usedPrice()).isEqualTo(latestEodhdPrice);
  }

  @Test
  void resolve_withZeroPricesAndNoLatestPrices_returnsNoPriceDataStatus() {
    mockPricesForDate(BigDecimal.ZERO, BigDecimal.ZERO);
    mockLatestPrices(null, null);

    Optional<ResolvedPrice> result = resolver.resolve(ISIN, DATE);

    assertThat(result).isPresent();
    ResolvedPrice resolved = result.get();
    assertThat(resolved.validationStatus()).isEqualTo(NO_PRICE_DATA);
  }

  @Test
  void resolve_withZeroYahooPrice_returnsYahooMissing() {
    BigDecimal eodhdPrice = new BigDecimal("100.00");
    mockPricesForDate(eodhdPrice, BigDecimal.ZERO);

    Optional<ResolvedPrice> result = resolver.resolve(ISIN, DATE);

    assertThat(result).isPresent();
    ResolvedPrice resolved = result.get();
    assertThat(resolved.validationStatus()).isEqualTo(YAHOO_MISSING);
    assertThat(resolved.usedPrice()).isEqualTo(eodhdPrice);
  }

  @Test
  void resolve_withZeroEodhdPrice_returnsEodhdMissingWithYahooSource() {
    BigDecimal yahooPrice = new BigDecimal("100.00");
    mockPricesForDate(BigDecimal.ZERO, yahooPrice);

    Optional<ResolvedPrice> result = resolver.resolve(ISIN, DATE);

    assertThat(result).isPresent();
    ResolvedPrice resolved = result.get();
    assertThat(resolved.validationStatus()).isEqualTo(EODHD_MISSING);
    assertThat(resolved.priceSource()).isEqualTo(YAHOO);
    assertThat(resolved.usedPrice()).isEqualTo(yahooPrice);
  }

  @Test
  void resolve_withUnknownIsin_returnsEmpty() {
    String unknownIsin = "UNKNOWN_ISIN";

    Optional<ResolvedPrice> result = resolver.resolve(unknownIsin, DATE);

    assertThat(result).isEmpty();
  }

  private void mockPricesForDate(BigDecimal eodhdPrice, BigDecimal yahooPrice) {
    when(fundValueProvider.getValueForDate(EODHD_TICKER, DATE))
        .thenReturn(toFundValue(EODHD_TICKER, eodhdPrice));
    when(fundValueProvider.getValueForDate(YAHOO_TICKER, DATE))
        .thenReturn(toFundValue(YAHOO_TICKER, yahooPrice));
  }

  private void mockLatestPrices(BigDecimal eodhdPrice, BigDecimal yahooPrice) {
    when(fundValueProvider.getLatestValue(EODHD_TICKER, DATE))
        .thenReturn(toFundValue(EODHD_TICKER, eodhdPrice, OLDER_DATE));
    when(fundValueProvider.getLatestValue(YAHOO_TICKER, DATE))
        .thenReturn(toFundValue(YAHOO_TICKER, yahooPrice, OLDER_DATE));
  }

  private Optional<FundValue> toFundValue(String key, BigDecimal value) {
    return toFundValue(key, value, DATE);
  }

  private Optional<FundValue> toFundValue(String key, BigDecimal value, LocalDate date) {
    if (value == null) {
      return Optional.empty();
    }
    return Optional.of(new FundValue(key, date, value, null, null));
  }
}
