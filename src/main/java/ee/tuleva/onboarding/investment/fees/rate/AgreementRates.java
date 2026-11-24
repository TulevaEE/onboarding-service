package ee.tuleva.onboarding.investment.fees.rate;

import static ee.tuleva.onboarding.investment.fees.rate.RateBasis.AGREEMENT;
import static ee.tuleva.onboarding.investment.fees.rate.RateBasis.PUBLISHED_FALLBACK;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueProvider;
import ee.tuleva.onboarding.investment.fees.rate.AgreementOutcome.Computed;
import ee.tuleva.onboarding.investment.fees.rate.AgreementOutcome.Uncomputable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
class AgreementRates {

  private static final String EUR_USD = "EURUSD.FOREX";
  private static final BigDecimal NO_REBATE = ZERO.setScale(8);
  private static final TypeReference<Map<String, Object>> TERMS = new TypeReference<>() {};

  private final MonthVolumeReader volumeReader;
  private final FundValueProvider fundValueProvider;
  private final RebateCalculator calculator;
  private final JsonMapper jsonMapper;

  ComputedRate ofTheMonth(InstrumentFeeAgreement agreement, YearMonth period) {
    AgreementTerms terms;
    try {
      terms = AgreementTerms.of(jsonMapper.readValue(agreement.rebateTerms(), TERMS));
    } catch (JacksonException | IllegalArgumentException unreadableTerms) {
      return couldNotBeApplied(agreement, period, unreadableTerms, null, null);
    }
    if (!terms.needTheMonthsVolume(agreement.rebateKind()) || terms.volumeFunds().isEmpty()) {
      return applied(agreement, period, terms, null);
    }
    return volumeReader
        .volumeOf(agreement.isin(), terms.volumeFunds(), period)
        .map(volume -> applied(agreement, period, terms, volume))
        .orElseGet(
            () ->
                publishedFallback(
                    agreement,
                    period,
                    "the month's volume cannot be measured: a fund named in the agreement has"
                        + " no published NAV for the day, funds="
                        + terms.volumeFunds(),
                    null,
                    null));
  }

  private ComputedRate applied(
      InstrumentFeeAgreement agreement,
      YearMonth period,
      AgreementTerms terms,
      @Nullable MonthVolume volume) {
    var eurUsd = terms.needAnExchangeRate() ? eurUsdOn(rateDate(volume, period)) : null;
    AgreementOutcome outcome;
    try {
      outcome =
          calculator.apply(
              agreement.rebateKind(),
              terms.values(),
              new RebateInputs(
                  agreement.publishedOcf(), volume == null ? null : volume.amount(), eurUsd));
    } catch (IllegalArgumentException unapplicableTerms) {
      return couldNotBeApplied(agreement, period, unapplicableTerms, volume, eurUsd);
    }
    return switch (outcome) {
      case Computed computed ->
          rate(
              agreement,
              period,
              computed.rebateRate(),
              computed.invoicedFeeRate().add(agreement.invoicedFeeRate()),
              AGREEMENT,
              null,
              volume,
              eurUsd);
      case Uncomputable uncomputable ->
          publishedFallback(agreement, period, uncomputable.reason(), volume, eurUsd);
    };
  }

  private ComputedRate couldNotBeApplied(
      InstrumentFeeAgreement agreement,
      YearMonth period,
      RuntimeException unreadableOrUnapplicable,
      @Nullable MonthVolume volume,
      @Nullable BigDecimal eurUsd) {
    return publishedFallback(
        agreement,
        period,
        "the agreement could not be applied: "
            + unreadableOrUnapplicable.getClass().getSimpleName(),
        volume,
        eurUsd);
  }

  private ComputedRate publishedFallback(
      InstrumentFeeAgreement agreement,
      YearMonth period,
      String reason,
      @Nullable MonthVolume volume,
      @Nullable BigDecimal eurUsd) {
    log.warn(
        "Instrument rate falls back to the published OCF: isin={}, period={}, reason={}",
        agreement.isin(),
        period,
        reason);
    return rate(
        agreement,
        period,
        NO_REBATE,
        agreement.invoicedFeeRate(),
        PUBLISHED_FALLBACK,
        reason,
        volume,
        eurUsd);
  }

  private static ComputedRate rate(
      InstrumentFeeAgreement agreement,
      YearMonth period,
      BigDecimal rebateRate,
      BigDecimal invoicedFeeRate,
      RateBasis rateBasis,
      @Nullable String fallbackReason,
      @Nullable MonthVolume volume,
      @Nullable BigDecimal eurUsd) {
    return new ComputedRate(
        agreement.isin(),
        period,
        agreement.publishedOcf(),
        rebateRate,
        invoicedFeeRate,
        rateBasis,
        fallbackReason,
        agreement.rebateKind(),
        volume == null ? null : volume.amount(),
        volume == null ? null : volume.navDate(),
        eurUsd,
        agreement.id());
  }

  private static LocalDate rateDate(@Nullable MonthVolume volume, YearMonth period) {
    var navDate = volume == null ? null : volume.navDate();
    return navDate == null ? period.atEndOfMonth() : navDate;
  }

  private @Nullable BigDecimal eurUsdOn(LocalDate date) {
    return fundValueProvider.getLatestValue(EUR_USD, date).map(FundValue::value).orElse(null);
  }
}
