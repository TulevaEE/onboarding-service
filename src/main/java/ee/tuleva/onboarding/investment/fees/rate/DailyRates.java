package ee.tuleva.onboarding.investment.fees.rate;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DailyRates {

  private final YearMonth month;
  private final Map<String, List<AgreementPeriod>> agreementsByIsin;
  private final Map<Long, InstrumentRate> rateByAgreement;

  public DailyRates(
      YearMonth month,
      List<AgreementPeriod> agreementsStartedByTheMonthEnd,
      Map<Long, InstrumentRate> rateByAgreement) {
    this.month = month;
    this.agreementsByIsin =
        agreementsStartedByTheMonthEnd.stream().collect(groupingBy(AgreementPeriod::isin));
    this.rateByAgreement = Map.copyOf(rateByAgreement);
  }

  public Optional<ApplicableRate> on(String isin, LocalDate date) {
    var day = date.isBefore(month.atDay(1)) ? month.atDay(1) : date;
    var agreements = agreementsByIsin.getOrDefault(isin, List.of());
    var inForce =
        agreements.stream()
            .filter(agreement -> agreement.inForceOn(day))
            .max(comparing(AgreementPeriod::validFrom));
    if (inForce.isPresent()) {
      var agreementId = inForce.get().id();
      return Optional.ofNullable(rateByAgreement.get(agreementId))
          .map(rate -> ApplicableRate.resolved(agreementId, rate));
    }
    return agreements.stream()
        .filter(agreement -> agreement.endedBefore(day))
        .max(comparing(AgreementPeriod::validFrom))
        .map(ApplicableRate::standardAfter);
  }
}
