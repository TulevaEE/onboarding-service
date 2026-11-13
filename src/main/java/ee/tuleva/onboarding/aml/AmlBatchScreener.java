package ee.tuleva.onboarding.aml;

import static ee.tuleva.onboarding.aml.AmlCheckType.POLITICALLY_EXPOSED_PERSON_AUTO;
import static ee.tuleva.onboarding.aml.AmlCheckType.SANCTION;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.AML;
import static ee.tuleva.onboarding.time.ClockHolder.aYearAgo;
import static java.util.stream.Collectors.toSet;

import ee.tuleva.onboarding.aml.notification.AmlChecksRunEvent;
import ee.tuleva.onboarding.analytics.ThirdPillarAnalytics;
import ee.tuleva.onboarding.analytics.ThirdPillarHolder;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.country.Countries;
import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
class AmlBatchScreener {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final DateTimeFormatter ESTONIAN_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

  private final ApplicationEventPublisher eventPublisher;
  private final ThirdPillarAnalytics thirdPillarAnalytics;
  private final SavingsFundCustomers savingsFundCustomers;
  private final UserRepository userRepository;
  private final OperationsNotificationService notificationService;
  private final SanctionAndPepScreener sanctionAndPepScreener;
  private final AmlCheckRepository amlCheckRepository;

  void runAmlChecksOnThirdPillarCustomers() {
    List<ThirdPillarHolder> customers = thirdPillarAnalytics.holdersInLatestSnapshot();
    eventPublisher.publishEvent(new AmlChecksRunEvent(this, customers.size()));
    screenBatch(ScreeningBatch.THIRD_PILLAR, customers, c -> Countries.of(c.country()));
  }

  void runAmlChecksOnThirdPillarHoldersWithoutRecentScreening() {
    try {
      LocalDate snapshotDate =
          thirdPillarAnalytics
              .latestSnapshotDate()
              .orElseThrow(() -> new IllegalStateException("No unit owner snapshot"));
      Set<String> screenedThisYear = personalCodesWithSanctionAndPepChecksThisYear();
      List<ThirdPillarHolder> unscreened =
          thirdPillarAnalytics.holdersInLatestSnapshot().stream()
              .filter(holder -> !screenedThisYear.contains(holder.personalCode()))
              .toList();
      int failureCount =
          screenBatch(ScreeningBatch.THIRD_PILLAR, unscreened, c -> Countries.of(c.country()));
      sendToAmlChannel(
          "III sammas (%s): viimase 12 kuu jooksul kontrollimata %d, kontrollitud %d, tõrkeid %d"
              .formatted(
                  snapshotLabel(snapshotDate),
                  unscreened.size(),
                  unscreened.size() - failureCount,
                  failureCount));
    } catch (RuntimeException e) {
      log.error("Daily third pillar AML screening failed", e);
      sendToAmlChannel("III sammas: igapäevane kontroll ebaõnnestus: reason=%s".formatted(e));
    }
  }

  private Set<String> personalCodesWithSanctionAndPepChecksThisYear() {
    Set<String> screened =
        new HashSet<>(
            amlCheckRepository.findPersonalCodesByTypeAndCreatedTimeAfter(SANCTION, aYearAgo()));
    screened.retainAll(
        amlCheckRepository.findPersonalCodesByTypeAndCreatedTimeAfter(
            POLITICALLY_EXPOSED_PERSON_AUTO, aYearAgo()));
    return screened;
  }

  private static String snapshotLabel(LocalDate snapshotDate) {
    String label = "registri seis " + snapshotDate.format(ESTONIAN_DATE);
    LocalDate today = LocalDate.now(ClockHolder.clock().withZone(TALLINN));
    return snapshotDate.isBefore(today) ? label + ", ei ole tänane" : label;
  }

  private void sendToAmlChannel(String message) {
    try {
      notificationService.sendMessage(message, AML);
    } catch (RuntimeException e) {
      log.error("Failed to send daily third pillar AML screening summary", e);
    }
  }

  void runAmlChecksOnSavingsFundCustomers() {
    List<String> holderCodes = savingsFundCustomers.personalCodes();
    List<SavingsFundGuardian> guardians = savingsFundCustomers.guardians();
    List<String> personalCodes =
        Stream.concat(
                holderCodes.stream(), guardians.stream().map(SavingsFundGuardian::personalCode))
            .distinct()
            .toList();
    List<User> users = userRepository.findAllByPersonalCodeIn(personalCodes);
    Set<String> userCodes = users.stream().map(User::getPersonalCode).collect(toSet());
    List<SavingsFundGuardian> guardiansWithoutUser =
        guardians.stream()
            .filter(guardian -> !userCodes.contains(guardian.personalCode()))
            .toList();
    List<Person> recordedGuardians =
        guardiansWithoutUser.stream()
            .map(SavingsFundGuardian::recordedPerson)
            .flatMap(Optional::stream)
            .toList();

    log.info(
        "Resolved savings fund customers to screen: onboarded={}, guardians={}, resolved={}, guardiansByRecordedName={}",
        holderCodes.size(),
        guardians.size(),
        users.size(),
        recordedGuardians.size());

    screenBatch(ScreeningBatch.SAVINGS_FUND, users, sanctionAndPepScreener::knownCountries);
    screenBatch(
        ScreeningBatch.SAVINGS_FUND_GUARDIANS,
        recordedGuardians,
        sanctionAndPepScreener::recordedCitizenships);
    alertOnUnscreenableGuardians(guardiansWithoutUser.size() - recordedGuardians.size());
  }

  private void alertOnUnscreenableGuardians(int unscreenableCount) {
    if (unscreenableCount == 0) {
      return;
    }
    log.error(
        "Savings fund guardians not screened, no user account or recorded name: count={}",
        unscreenableCount);
    try {
      notificationService.sendMessage(
          "AML batch: %d savings fund guardians have no user account or recorded name and were not screened this run"
              .formatted(unscreenableCount),
          AML);
    } catch (RuntimeException e) {
      log.error("Failed to send unscreenable savings fund guardians alert", e);
    }
  }

  private <T extends Person> int screenBatch(
      ScreeningBatch batch, List<T> people, Function<T, Set<Country>> countriesOf) {
    log.info(
        "Running AML screening batch: population={}, people={}", batch.population, people.size());

    int failureCount = 0;
    for (T person : people) {
      try {
        if (sanctionAndPepScreener
            .screenForSanctionAndPep(person, countriesOf.apply(person))
            .failed()) {
          failureCount++;
        }
      } catch (RuntimeException e) {
        sanctionAndPepScreener.handleScreeningFailure(person, batch.metricPhase, e);
        failureCount++;
      }
    }

    alertOnBatchScreeningFailures(batch, failureCount, people.size());

    log.info(
        "Finished AML screening batch: population={}, people={}, screeningFailures={}",
        batch.population,
        people.size(),
        failureCount);
    return failureCount;
  }

  private void alertOnBatchScreeningFailures(
      ScreeningBatch batch, int failureCount, int peopleCount) {
    if (failureCount == 0) {
      return;
    }
    try {
      notificationService.sendMessage(
          "AML batch: sanction/PEP screening failed for %d of %d %s customers this run"
              .formatted(failureCount, peopleCount, batch.population),
          AML);
    } catch (RuntimeException e) {
      log.error(
          "Failed to send aggregated AML batch screening-failure alert: population={}",
          batch.population,
          e);
    }
  }
}
