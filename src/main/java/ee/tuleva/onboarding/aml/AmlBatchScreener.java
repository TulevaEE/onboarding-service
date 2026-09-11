package ee.tuleva.onboarding.aml;

import ee.tuleva.onboarding.aml.notification.AmlChecksRunEvent;
import ee.tuleva.onboarding.analytics.RecentThirdPillarCustomer;
import ee.tuleva.onboarding.analytics.ThirdPillarAnalytics;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.country.Countries;
import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserRepository;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
class AmlBatchScreener {

  private final ApplicationEventPublisher eventPublisher;
  private final ThirdPillarAnalytics thirdPillarAnalytics;
  private final SavingsFundCustomers savingsFundCustomers;
  private final UserRepository userRepository;
  private final OperationsNotificationService notificationService;
  private final SanctionAndPepScreener sanctionAndPepScreener;

  void runAmlChecksOnThirdPillarCustomers() {
    List<RecentThirdPillarCustomer> customers = thirdPillarAnalytics.recentCustomers();
    eventPublisher.publishEvent(new AmlChecksRunEvent(this, customers.size()));
    screenBatch(ScreeningBatch.THIRD_PILLAR, customers, c -> Countries.of(c.country()));
  }

  void runAmlChecksOnSavingsFundCustomers() {
    List<String> personalCodes = savingsFundCustomers.personalCodes();
    List<User> customers = userRepository.findAllByPersonalCodeIn(personalCodes);

    log.info(
        "Resolved savings fund customers to screen: onboarded={}, resolved={}",
        personalCodes.size(),
        customers.size());

    screenBatch(ScreeningBatch.SAVINGS_FUND, customers, sanctionAndPepScreener::knownCountries);
  }

  private <T extends Person> void screenBatch(
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
          OperationsNotificationService.Channel.AML);
    } catch (RuntimeException e) {
      log.error(
          "Failed to send aggregated AML batch screening-failure alert: population={}",
          batch.population,
          e);
    }
  }
}
