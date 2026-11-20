package ee.tuleva.onboarding.party;

import static java.util.function.Predicate.not;

import ee.tuleva.onboarding.aml.RegisterRequester;
import ee.tuleva.onboarding.analytics.UnitOwnerSnapshotSynchronized;
import ee.tuleva.onboarding.personalcode.PersonalCode;
import ee.tuleva.onboarding.personalcode.PersonalCodeValidator;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AutomaticGuardianLookups {

  static final int MAX_PER_RUN = 50;
  static final int MAX_BACKFILL_PER_RUN = 1000;
  static final LocalDate LAST_BACKFILL_DAY_AT_THE_2026_POPULATION_REGISTER_PRICE =
      LocalDate.of(2026, 12, 31);

  private static final PersonalCodeValidator PERSONAL_CODES = new PersonalCodeValidator();

  private final MinorGuardianLookupService minorGuardianLookupService;
  private final AutomaticGuardianLookupCandidates candidates;
  private final GuardianLookupRepository guardianLookupRepository;
  private final RegisterRequester registerRequester;
  private final Clock clock;
  private final Duration pause;

  @Autowired
  AutomaticGuardianLookups(
      MinorGuardianLookupService minorGuardianLookupService,
      AutomaticGuardianLookupCandidates candidates,
      GuardianLookupRepository guardianLookupRepository,
      RegisterRequester registerRequester,
      Clock clock,
      @Value("${guardian-lookup.automatic-pause:PT1S}") Duration pause) {
    this.minorGuardianLookupService = minorGuardianLookupService;
    this.candidates = candidates;
    this.guardianLookupRepository = guardianLookupRepository;
    this.registerRequester = registerRequester;
    this.clock = clock;
    this.pause = pause;
  }

  public void lookUpOnce(String childPersonalCode) {
    designatedRequester().ifPresent(requester -> lookUpOnce(requester, childPersonalCode));
  }

  private void lookUpOnce(String requester, String childPersonalCode) {
    try {
      minorGuardianLookupService
          .lookUpChildOnce(requester, childPersonalCode)
          .ifPresent(lookup -> log.info("Automatic guardian lookup of one child done"));
    } catch (RuntimeException e) {
      logFailure(e);
    }
  }

  @Async
  @EventListener(UnitOwnerSnapshotSynchronized.class)
  public void onUnitOwnerSnapshot() {
    designatedRequester()
        .ifPresent(
            requester -> {
              lookUpNewHoldersAndPayouts(requester);
              backfillMinorThirdPillarHolders(requester);
            });
  }

  private void lookUpNewHoldersAndPayouts(String requester) {
    List<String> children = candidates();
    log.info("Starting automatic guardian lookups: children={}", children.size());
    int lookedUp = 0;
    int failed = 0;
    for (String child : children) {
      try {
        minorGuardianLookupService.lookUpChildOnce(requester, child);
        lookedUp++;
      } catch (RuntimeException e) {
        failed++;
        logFailure(e);
      }
      if (!pauseBetweenQueries()) {
        break;
      }
    }
    log.info(
        "Finished automatic guardian lookups: children={}, lookedUp={}, failed={}",
        children.size(),
        lookedUp,
        failed);
  }

  public MinorGuardianLookupRun backfillNow(int batchSize) {
    if (backfillIsOver()) {
      throw new MinorGuardianBackfillUnavailable();
    }
    String requester = designatedRequester().orElseThrow(MinorGuardianBackfillUnavailable::new);
    return backfill(requester, batchSize);
  }

  private void backfillMinorThirdPillarHolders(String requester) {
    if (Thread.currentThread().isInterrupted() || backfillIsOver()) {
      return;
    }
    try {
      backfill(requester, MAX_BACKFILL_PER_RUN);
    } catch (RuntimeException e) {
      log.error(
          "Minor guardian backfill did not run, it is tried again at the next snapshot: {}",
          RegisterFailure.of(e));
    }
  }

  private MinorGuardianLookupRun backfill(String requester, int batchSize) {
    return minorGuardianLookupService.backfillMinorThirdPillarHolders(requester, batchSize, pause);
  }

  private boolean backfillIsOver() {
    return LocalDate.now(clock).isAfter(LAST_BACKFILL_DAY_AT_THE_2026_POPULATION_REGISTER_PRICE);
  }

  List<String> candidates() {
    LocalDate today = LocalDate.now(clock);
    Set<String> lookedUp = Set.copyOf(guardianLookupRepository.findAllChildPersonalCodes());
    return candidates.firstSeenHoldersAndRecentThirdPillarPayouts().stream()
        .filter(PERSONAL_CODES::isValid)
        .filter(code -> PersonalCode.isMinor(code, today))
        .filter(not(lookedUp::contains))
        .distinct()
        .sorted()
        .limit(MAX_PER_RUN)
        .toList();
  }

  private Optional<String> designatedRequester() {
    Optional<String> requester;
    try {
      requester = registerRequester.designatedXRoadUserId();
    } catch (RuntimeException e) {
      log.error(
          "Automatic guardian lookup skipped, aml-service did not name the requester, it is asked"
              + " again at the next run: error={}",
          e.getClass().getSimpleName());
      return Optional.empty();
    }
    if (requester.isEmpty()) {
      log.info("Automatic guardian lookup skipped, no requester designated in aml-service");
    }
    return requester;
  }

  private static void logFailure(RuntimeException e) {
    log.error(
        "Automatic guardian lookup failed, the child is asked again at the next event: {}",
        RegisterFailure.of(e));
  }

  private boolean pauseBetweenQueries() {
    try {
      Thread.sleep(pause);
      return true;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }
}
