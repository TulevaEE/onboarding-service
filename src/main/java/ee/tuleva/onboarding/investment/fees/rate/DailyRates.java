package ee.tuleva.onboarding.investment.fees.rate;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DailyRates {

  private final YearMonth month;
  private final AgreementsInForce agreements;
  private final Map<Long, InstrumentRate> rateByAgreement;

  public DailyRates(
      YearMonth month,
      List<AgreementPeriod> agreementsStartedByTheMonthEnd,
      Map<Long, InstrumentRate> rateByAgreement) {
    this.month = month;
    this.agreements = new AgreementsInForce(agreementsStartedByTheMonthEnd);
    this.rateByAgreement = Map.copyOf(rateByAgreement);
  }

  public Optional<ApplicableRate> on(String isin, LocalDate date) {
    var day = date.isBefore(month.atDay(1)) ? month.atDay(1) : date;
    var inForce = agreements.on(isin, day);
    if (inForce.isPresent()) {
      var agreementId = inForce.get().id();
      return Optional.ofNullable(rateByAgreement.get(agreementId))
          .map(rate -> ApplicableRate.resolved(agreementId, rate));
    }
    return agreements.lastEndedBefore(isin, day).map(ApplicableRate::standardAfter);
  }

  public List<InstrumentRate> ratesAppliedDuringTheMonth() {
    return agreements.ratesAppliedDuring(month, rateByAgreement);
  }
}
