package ee.tuleva.onboarding.comparisons.returns;

import static ee.tuleva.onboarding.comparisons.returns.provider.PersonalReturnProvider.THIRD_PILLAR;
import static java.time.temporal.ChronoUnit.DAYS;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import ee.tuleva.onboarding.comparisons.returns.Returns.Return;
import ee.tuleva.onboarding.comparisons.returns.provider.ReturnCalculationParameters;
import ee.tuleva.onboarding.comparisons.returns.provider.ReturnProvider;
import ee.tuleva.onboarding.deadline.MandateDeadlines;
import ee.tuleva.onboarding.deadline.MandateDeadlinesService;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReturnsService {

  private final List<ReturnProvider> returnProviders;
  private final FundValueRepository fundValueRepository;
  private final MandateDeadlinesService mandateDeadlinesService;

  public Returns get(Person person, LocalDate fromDate, LocalDate endDate, List<String> keys) {
    int pillar = getPillar(keys);
    Instant fromTime = getRevisedFromTime(fromDate, keys, pillar);
    Instant toTime = endDate.atStartOfDay().atZone(ZoneOffset.UTC).toInstant();

    List<Return> allReturns = new ArrayList<>();

    for (ReturnProvider provider : returnProviders) {
      List<String> relevantKeysForTheProvider = getRelevantKeysForTheProvider(keys, provider);

      Returns providerReturns =
          provider.getReturns(
              new ReturnCalculationParameters(
                  person, fromTime, toTime, pillar, relevantKeysForTheProvider));

      if (providerReturns != null && providerReturns.getReturns() != null) {
        allReturns.addAll(providerReturns.getReturns());
      }
    }

    return Returns.builder().returns(filterReturnsBasedOnInputKeys(keys, allReturns)).build();
  }

  private List<Return> filterReturnsBasedOnInputKeys(List<String> keys, List<Return> allReturns) {
    if (keys != null && !keys.isEmpty()) {
      allReturns.removeIf(returnObj -> !keys.contains(returnObj.getKey()));
    }
    return allReturns;
  }

  @NotNull
  private List<String> getRelevantKeysForTheProvider(List<String> keys, ReturnProvider provider) {
    List<String> relevantKeysForTheProvider = new ArrayList<>(provider.getKeys());

    if (keys != null) {
      relevantKeysForTheProvider.retainAll(keys);
    }
    return relevantKeysForTheProvider;
  }

  private Instant getRevisedFromTime(LocalDate fromDate, List<String> keys, int pillar) {
    LocalDate earliestNavDate = chooseDateAccordingToDataAvailability(fromDate, keys);
    Instant earliestNavTime = earliestNavDate.atStartOfDay().atZone(ZoneOffset.UTC).toInstant();

    if (earliestNavDate.equals(fromDate) || pillar == 3) {
      // so you could always get the previous day's nav for the beginning balance
      return earliestNavTime.plus(2, DAYS);
    }

    MandateDeadlines deadlines = mandateDeadlinesService.getDeadlines(earliestNavTime);
    PublicHolidays publicHolidays = new PublicHolidays();
    LocalDate transferMandateFulfillmentDate = deadlines.getTransferMandateFulfillmentDate();
    LocalDate navDatePlus1 =
        publicHolidays.previousWorkingDay(transferMandateFulfillmentDate).plusDays(1);
    return navDatePlus1.atStartOfDay().atZone(ZoneOffset.UTC).toInstant();
  }

  private LocalDate chooseDateAccordingToDataAvailability(LocalDate fromDate, List<String> keys) {
    if (keys == null) return fromDate;

    Optional<LocalDate> latestKeyDataStartDate =
        keys.stream()
            .map(fundValueRepository::findEarliestDateForKey)
            .flatMap(Optional::stream)
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
