package ee.tuleva.onboarding.comparisons.returns;

import static ee.tuleva.onboarding.comparisons.returns.provider.PersonalReturnProvider.THIRD_PILLAR;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import ee.tuleva.onboarding.comparisons.returns.Returns.Return;
import ee.tuleva.onboarding.comparisons.returns.provider.ReturnProvider;
import ee.tuleva.onboarding.deadline.MandateDeadlines;
import ee.tuleva.onboarding.deadline.MandateDeadlinesService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReturnsService {

  private final List<ReturnProvider> returnProviders;
  private final FundValueRepository fundValueRepository;
  private final MandateDeadlinesService mandateDeadlinesService;

  public Returns get(Person person, LocalDate fromDate, List<String> keys) {
    int pillar = getPillar(keys);
    Instant fromTime = getRevisedFromTime(fromDate, keys);

    List<Return> returns =
        returnProviders.stream()
            .filter(
                returnProvider ->
                    keys == null || !Collections.disjoint(keys, returnProvider.getKeys()))
            .map(returnProvider -> returnProvider.getReturns(person, fromTime, pillar).getReturns())
            .flatMap(List::stream)
            .filter(aReturn -> keys == null || keys.contains(aReturn.getKey()))
            .toList();

    return Returns.builder().returns(returns).build();
  }

  private Instant getRevisedFromTime(LocalDate fromDate, List<String> keys) {
    LocalDate earliestNavDate = chooseDateAccordingToDataAvailability(fromDate, keys);
    Instant earliestNavTime = earliestNavDate.atStartOfDay().atZone(ZoneOffset.UTC).toInstant();
    MandateDeadlines deadlines = mandateDeadlinesService.getDeadlines(earliestNavTime);
    return deadlines
        .getTransferMandateFulfillmentDate()
        .atStartOfDay()
        .atZone(ZoneOffset.UTC)
        .toInstant();
  }

  private LocalDate chooseDateAccordingToDataAvailability(LocalDate fromDate, List<String> keys) {
    if (keys == null) return fromDate;

    Optional<LocalDate> latestKeyDataStartDate =
        keys.stream()
            .map(fundValueRepository::findEarliestDateForKey)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .max(LocalDate::compareTo);

    return latestKeyDataStartDate
        .filter(latestDate -> latestDate.isAfter(fromDate))
        .orElse(fromDate);
  }

  private Integer getPillar(List<String> keys) {
    if (keys != null && keys.contains(THIRD_PILLAR)) {
      return 3;
    } else {
      return 2;
    }
  }
}
