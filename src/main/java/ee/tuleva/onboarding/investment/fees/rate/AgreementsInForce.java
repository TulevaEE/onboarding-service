package ee.tuleva.onboarding.investment.fees.rate;

import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsLast;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toSet;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

final class AgreementsInForce {

  private final Map<String, List<AgreementPeriod>> agreementsByIsin;

  AgreementsInForce(List<AgreementPeriod> agreements) {
    this.agreementsByIsin = agreements.stream().collect(groupingBy(AgreementPeriod::isin));
  }

  Optional<AgreementPeriod> on(String isin, LocalDate day) {
    return agreementsOf(isin)
        .filter(agreement -> agreement.inForceOn(day))
        .max(comparing(AgreementPeriod::validFrom));
  }

  Optional<AgreementPeriod> lastEndedBefore(String isin, LocalDate day) {
    return agreementsOf(isin)
        .filter(agreement -> agreement.endedBefore(day))
        .max(
            comparing(AgreementPeriod::validTo, nullsLast(naturalOrder()))
                .thenComparing(AgreementPeriod::validFrom));
  }

  Set<Long> appliedDuring(YearMonth month) {
    return month
        .atDay(1)
        .datesUntil(month.plusMonths(1).atDay(1))
        .flatMap(day -> agreementsByIsin.keySet().stream().flatMap(isin -> on(isin, day).stream()))
        .map(AgreementPeriod::id)
        .collect(toSet());
  }

  List<InstrumentRate> ratesAppliedDuring(
      YearMonth month, Map<Long, InstrumentRate> rateByAgreement) {
    var applied = appliedDuring(month);
    return rateByAgreement.entrySet().stream()
        .filter(rate -> applied.contains(rate.getKey()))
        .map(Map.Entry::getValue)
        .sorted(comparing(InstrumentRate::isin).thenComparing(InstrumentRate::id))
        .toList();
  }

  private Stream<AgreementPeriod> agreementsOf(String isin) {
    return agreementsByIsin.getOrDefault(isin, List.of()).stream();
  }
}
