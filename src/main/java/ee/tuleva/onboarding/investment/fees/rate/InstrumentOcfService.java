package ee.tuleva.onboarding.investment.fees.rate;

import static ee.tuleva.onboarding.investment.fees.rate.InstrumentFeeRateRepository.closeOf;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InstrumentOcfService {

  private final InstrumentFeeAgreementRepository agreementRepository;
  private final InstrumentFeeRateRepository rateRepository;
  private final AgreementRates agreementRates;

  @Transactional
  public List<InstrumentRate> resolve(YearMonth period) {
    var applied = agreementRepository.findIdsAppliedDuring(period);
    return agreementRepository.findAllInForceDuring(period).stream()
        .filter(agreement -> applied.contains(agreement.id()))
        .map(agreement -> rateRepository.append(agreementRates.ofTheMonth(agreement, period)))
        .toList();
  }

  public DailyRates dailyRatesFor(YearMonth period) {
    return new DailyRates(
        period,
        agreementRepository.findAllStartedBy(period.atEndOfMonth()),
        rateRepository.findNewestPerAgreementFor(period));
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
    var resolvedAfterItClosed = rateRepository.agreementsResolvedSince(period, closeOf(period));
    return resolvedAfterItClosed.containsAll(agreementRepository.findIdsAppliedDuring(period));
  }
}
