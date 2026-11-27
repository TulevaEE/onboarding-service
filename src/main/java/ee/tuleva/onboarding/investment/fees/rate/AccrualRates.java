package ee.tuleva.onboarding.investment.fees.rate;

import static ee.tuleva.onboarding.investment.fees.rate.InstrumentFeeRateRepository.closeOf;
import static java.util.stream.Collectors.toCollection;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccrualRates {

  private final InstrumentFeeAgreementRepository agreementRepository;
  private final InstrumentFeeRateRepository rateRepository;
  private final AgreementRates agreementRates;

  public List<AccrualRate> on(LocalDate day) {
    var period = YearMonth.from(day);
    var resolved = rateRepository.findNewestPerAgreementResolvedSince(period, closeOf(period));
    return agreementRepository.findAllValidOn(day).stream()
        .flatMap(
            agreement ->
                Stream.ofNullable(agreement.providerAgreementId())
                    .map(
                        providerAgreementId ->
                            Optional.ofNullable(resolved.get(agreement.id()))
                                .map(
                                    rate ->
                                        AccrualRate.resolved(agreement, providerAgreementId, rate))
                                .orElseGet(
                                    () ->
                                        AccrualRate.provisional(
                                            agreement,
                                            providerAgreementId,
                                            agreementRates.soFar(agreement, day)))))
        .toList();
  }

  public SortedSet<String> isinsWithoutAProviderAgreementOn(LocalDate day) {
    return agreementRepository.findAllValidOn(day).stream()
        .filter(agreement -> agreement.providerAgreementId() == null)
        .map(InstrumentFeeAgreement::isin)
        .collect(toCollection(TreeSet::new));
  }
}
