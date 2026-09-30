package ee.tuleva.onboarding.investment.fees.rate;

import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;
import static ee.tuleva.onboarding.investment.fees.rate.RateBasis.AGREEMENT;
import static ee.tuleva.onboarding.investment.fees.rate.RateBasis.PUBLISHED_FALLBACK;
import static java.math.BigDecimal.ZERO;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueProvider;
import ee.tuleva.onboarding.investment.fees.rate.AgreementOutcome.Computed;
import ee.tuleva.onboarding.investment.fees.rate.AgreementOutcome.Uncomputable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class InstrumentOcfService {

  private static final String EUR_USD = "EURUSD.FOREX";
  private static final BigDecimal NO_REBATE = ZERO.setScale(8);
  private static final TypeReference<Map<String, Object>> TERMS = new TypeReference<>() {};

  private final InstrumentFeeAgreementRepository agreementRepository;
  private final InstrumentFeeRateRepository rateRepository;
  private final MonthVolumeReader volumeReader;
  private final FundValueProvider fundValueProvider;
  private final RebateCalculator calculator;
  private final JsonMapper jsonMapper;

  @Transactional
  public List<InstrumentRate> resolve(YearMonth period) {
    return agreementRepository.findAllValidOn(period.atEndOfMonth()).stream()
        .map(agreement -> rateRepository.append(compute(agreement, period)))
        .toList();
  }

  public Map<String, InstrumentRate> ratesFor(YearMonth period) {
    return rateRepository.findNewestFor(period).stream()
        .collect(toMap(InstrumentRate::isin, identity(), (first, second) -> first, TreeMap::new));
  }

  public Set<String> isinsWithAnAgreementOn(LocalDate date) {
    return agreementRepository.findAllValidOn(date).stream()
        .map(InstrumentFeeAgreement::isin)
        .collect(toSet());
  }

  public boolean hasRatesResolvedAfterItClosed(YearMonth period) {
    var resolvedAfterItClosed =
        rateRepository.agreementsResolvedSince(
            period, period.plusMonths(1).atDay(1).atStartOfDay(ZoneId.of(TIMEZONE)).toInstant());
    return agreementRepository.findAllValidOn(period.atEndOfMonth()).stream()
        .map(InstrumentFeeAgreement::id)
        .allMatch(resolvedAfterItClosed::contains);
  }

  private ComputedRate compute(InstrumentFeeAgreement agreement, YearMonth period) {
    try {
      var terms = new AgreementTerms(jsonMapper.readValue(agreement.rebateTerms(), TERMS));
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
    } catch (RuntimeException unreadableAgreement) {
      return publishedFallback(
          agreement,
          period,
          "the agreement could not be applied: " + unreadableAgreement.getClass().getSimpleName(),
          null,
          null);
    }
  }

  private ComputedRate applied(
      InstrumentFeeAgreement agreement,
      YearMonth period,
      AgreementTerms terms,
      @Nullable MonthVolume volume) {
    var eurUsd = terms.needAnExchangeRate() ? eurUsdOn(rateDate(volume, period)) : null;
    var outcome =
        calculator.apply(
            agreement.rebateKind(),
            terms.values(),
            new RebateInputs(
                agreement.publishedOcf(), volume == null ? null : volume.amount(), eurUsd));
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
