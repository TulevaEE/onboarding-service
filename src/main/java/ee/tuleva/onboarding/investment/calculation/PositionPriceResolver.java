package ee.tuleva.onboarding.investment.calculation;

import static ee.tuleva.onboarding.investment.calculation.PriceSource.EODHD;
import static ee.tuleva.onboarding.investment.calculation.PriceSource.YAHOO;
import static ee.tuleva.onboarding.investment.calculation.ValidationStatus.EODHD_MISSING;
import static ee.tuleva.onboarding.investment.calculation.ValidationStatus.NO_PRICE_DATA;
import static ee.tuleva.onboarding.investment.calculation.ValidationStatus.OK;
import static ee.tuleva.onboarding.investment.calculation.ValidationStatus.PRICE_DISCREPANCY;
import static ee.tuleva.onboarding.investment.calculation.ValidationStatus.YAHOO_MISSING;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueProvider;
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundTicker;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PositionPriceResolver {

  private static final BigDecimal DISCREPANCY_THRESHOLD_PERCENT = new BigDecimal("0.1");
  private static final int PERCENTAGE_SCALE = 4;

  private final FundValueProvider fundValueProvider;

  public Optional<ResolvedPrice> resolve(String isin, LocalDate date) {
    return FundTicker.findByIsin(isin).map(ticker -> resolveForTicker(ticker, date));
  }

  private ResolvedPrice resolveForTicker(FundTicker ticker, LocalDate date) {
    FundValue eodhdValue = fetchLatestValue(ticker.getEodhdTicker(), date);
    FundValue yahooValue = fetchLatestValue(ticker.getYahooTicker(), date);
    return determineResult(eodhdValue, yahooValue);
  }

  private FundValue fetchLatestValue(String tickerKey, LocalDate maxDate) {
    return fundValueProvider
        .getLatestValue(tickerKey, maxDate)
        .filter(fundValue -> fundValue.value().compareTo(ZERO) != 0)
        .orElse(null);
  }

  private ResolvedPrice determineResult(FundValue eodhdValue, FundValue yahooValue) {
    boolean hasEodhd = eodhdValue != null;
    boolean hasYahoo = yahooValue != null;

    if (hasEodhd && hasYahoo) {
      return resolveWithBothPrices(eodhdValue, yahooValue);
    }

    if (hasEodhd) {
      return ResolvedPrice.builder()
          .eodhdPrice(eodhdValue.value())
          .yahooPrice(null)
          .usedPrice(eodhdValue.value())
          .priceSource(EODHD)
          .validationStatus(YAHOO_MISSING)
          .discrepancyPercent(null)
          .priceDate(eodhdValue.date())
          .build();
    }

    if (hasYahoo) {
      return ResolvedPrice.builder()
          .eodhdPrice(null)
          .yahooPrice(yahooValue.value())
          .usedPrice(yahooValue.value())
          .priceSource(YAHOO)
          .validationStatus(EODHD_MISSING)
          .discrepancyPercent(null)
          .priceDate(yahooValue.date())
          .build();
    }

    return ResolvedPrice.builder()
        .eodhdPrice(null)
        .yahooPrice(null)
        .usedPrice(null)
        .priceSource(null)
        .validationStatus(NO_PRICE_DATA)
        .discrepancyPercent(null)
        .priceDate(null)
        .build();
  }

  private ResolvedPrice resolveWithBothPrices(FundValue eodhdValue, FundValue yahooValue) {
    BigDecimal eodhdPrice = eodhdValue.value();
    BigDecimal yahooPrice = yahooValue.value();
    BigDecimal discrepancyPercent = calculateDiscrepancyPercent(eodhdPrice, yahooPrice);
    boolean hasDiscrepancy = discrepancyPercent.compareTo(DISCREPANCY_THRESHOLD_PERCENT) > 0;

    return ResolvedPrice.builder()
        .eodhdPrice(eodhdPrice)
        .yahooPrice(yahooPrice)
        .usedPrice(eodhdPrice)
        .priceSource(EODHD)
        .validationStatus(hasDiscrepancy ? PRICE_DISCREPANCY : OK)
        .discrepancyPercent(discrepancyPercent)
        .priceDate(eodhdValue.date())
        .build();
  }

  private BigDecimal calculateDiscrepancyPercent(BigDecimal eodhdPrice, BigDecimal yahooPrice) {
    if (yahooPrice.compareTo(ZERO) == 0) {
      return eodhdPrice.compareTo(ZERO) == 0 ? ZERO : new BigDecimal("100");
    }

    return eodhdPrice
        .subtract(yahooPrice)
        .abs()
        .multiply(new BigDecimal("100"))
        .divide(yahooPrice.abs(), PERCENTAGE_SCALE, HALF_UP);
  }
}
