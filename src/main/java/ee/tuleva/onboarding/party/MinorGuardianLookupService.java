package ee.tuleva.onboarding.party;

import static java.util.function.Predicate.not;

import ee.tuleva.onboarding.analytics.ThirdPillarAnalytics;
import ee.tuleva.onboarding.analytics.ThirdPillarHolder;
import ee.tuleva.onboarding.personalcode.PersonalCode;
import ee.tuleva.onboarding.personalcode.PersonalCodeValidator;
import ee.tuleva.onboarding.populationregister.PopulationRegisterClient;
import ee.tuleva.onboarding.populationregister.PopulationRegisterUnavailable;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinorGuardianLookupService {

  public static final Duration PAUSE_BETWEEN_QUERIES = Duration.ofMillis(200);

  private static final PersonalCodeValidator PERSONAL_CODES = new PersonalCodeValidator();

  private final ThirdPillarAnalytics thirdPillarAnalytics;
  private final PopulationRegisterClient populationRegisterClient;
  private final GuardianLookupRepository guardianLookupRepository;
  private final MinorGuardianLookupLock lock;
  private final Clock clock;

  public MinorGuardianLookupRun lookUpMinorThirdPillarHolders(
      String requesterPersonalCode, int batchSize, Duration pause) {
    SimpleLock held = lock.acquire().orElseThrow(MinorGuardianLookupAlreadyRunning::new);
    try {
      return lookUpOneBatch(requesterPersonalCode, batchSize, pause);
    } finally {
      held.unlock();
    }
  }

  private MinorGuardianLookupRun lookUpOneBatch(
      String requesterPersonalCode, int batchSize, Duration pause) {
    List<String> notLookedUp = minorHoldersNotLookedUp();
    List<String> batch = notLookedUp.subList(0, Math.min(batchSize, notLookedUp.size()));
    log.info(
        "Starting minor guardian lookup: notLookedUp={}, batchSize={}",
        notLookedUp.size(),
        batchSize);
    int lookedUp = 0;
    int failed = 0;
    for (int position = 1; position <= batch.size(); position++) {
      try {
        lookUp(requesterPersonalCode, batch.get(position - 1));
        lookedUp++;
      } catch (PopulationRegisterUnavailable e) {
        log.error(
            "Population register unavailable, stopping minor guardian lookup: position={},"
                + " batch={}, error={}",
            position,
            batch.size(),
            e.getClass().getSimpleName());
        failed++;
        break;
      } catch (RuntimeException e) {
        log.error(
            "Minor guardian lookup failed: position={}, batch={}, error={}",
            position,
            batch.size(),
            e.getClass().getSimpleName());
        failed++;
      }
      if (!pauseBetweenQueries(pause)) {
        break;
      }
    }
    var run = new MinorGuardianLookupRun(lookedUp, failed, notLookedUp.size() - lookedUp);
    log.info(
        "Finished minor guardian lookup: lookedUp={}, failed={}, remaining={}",
        run.lookedUp(),
        run.failed(),
        run.remaining());
    return run;
  }

  private List<String> minorHoldersNotLookedUp() {
    LocalDate today = LocalDate.now(clock);
    Set<String> lookedUp = Set.copyOf(guardianLookupRepository.findAllChildPersonalCodes());
    return thirdPillarAnalytics.holdersInLatestSnapshot().stream()
        .map(ThirdPillarHolder::personalCode)
        .map(String::strip)
        .filter(PERSONAL_CODES::isValid)
        .filter(code -> PersonalCode.isMinor(code, today))
        .filter(not(lookedUp::contains))
        .distinct()
        .sorted()
        .toList();
  }

  private void lookUp(String requesterPersonalCode, String childPersonalCode) {
    guardianLookupRepository.save(
        GuardianLookup.of(
            childPersonalCode,
            populationRegisterClient.fetchGuardians(requesterPersonalCode, childPersonalCode),
            clock.instant()));
  }

  private static boolean pauseBetweenQueries(Duration pause) {
    try {
      Thread.sleep(pause);
      return true;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }
}
