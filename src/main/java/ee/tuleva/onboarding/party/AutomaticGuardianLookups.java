package ee.tuleva.onboarding.party;

import static java.util.function.Predicate.not;

import ee.tuleva.onboarding.analytics.ThirdPillarAnalytics;
import ee.tuleva.onboarding.analytics.ThirdPillarHolder;
import ee.tuleva.onboarding.analytics.UnitOwnerSnapshotSynchronized;
import ee.tuleva.onboarding.personalcode.PersonalCode;
import ee.tuleva.onboarding.personalcode.PersonalCodeValidator;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
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
  private final ThirdPillarAnalytics thirdPillarAnalytics;
  private final GuardianLookupRepository guardianLookupRepository;
  private final Clock clock;
  private final @Nullable String requester;
  private final Duration pause;

  @Autowired
  AutomaticGuardianLookups(
      MinorGuardianLookupService minorGuardianLookupService,
      AutomaticGuardianLookupCandidates candidates,
      ThirdPillarAnalytics thirdPillarAnalytics,
      GuardianLookupRepository guardianLookupRepository,
      Clock clock,
      @Value("${guardian-lookup.automatic-requester:#{@populationRegisterMember.memberCode()}}")
          @Nullable String requester,
      @Value("${guardian-lookup.automatic-pause:PT1S}") Duration pause) {
    this.minorGuardianLookupService = minorGuardianLookupService;
    this.candidates = candidates;
    this.thirdPillarAnalytics = thirdPillarAnalytics;
    this.guardianLookupRepository = guardianLookupRepository;
    this.clock = clock;
    this.requester = requester == null || requester.isBlank() ? null : requester;
    this.pause = pause;
  }

  public void lookUpOnce(String childPersonalCode) {
    if (requester == null) {
      logSkipped();
      return;
    }
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
    String asRequester = requester;
    if (asRequester == null) {
      logSkipped();
      return;
    }
    lookUpNewHoldersAndPayouts(asRequester);
    backfillMinorThirdPillarHolders(asRequester);
  }

  void lookUpNewHoldersAndPayouts(String asRequester) {
    List<String> children = candidates();
    log.info("Starting automatic guardian lookups: children={}", children.size());
    int lookedUp = 0;
    int failed = 0;
    for (String child : children) {
      try {
        minorGuardianLookupService.lookUpChildOnce(asRequester, child);
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

  void backfillMinorThirdPillarHolders(String asRequester) {
    if (LocalDate.now(clock).isAfter(LAST_BACKFILL_DAY_AT_THE_2026_POPULATION_REGISTER_PRICE)) {
      return;
    }
    List<String> remaining = backfillCandidates();
    if (remaining.isEmpty()) {
      return;
    }
    List<String> batch = remaining.stream().limit(MAX_BACKFILL_PER_RUN).toList();
    log.info(
        "Starting guardian lookup backfill: remaining={}, batch={}",
        remaining.size(),
        batch.size());
    int lookedUp = 0;
    for (String child : batch) {
      try {
        minorGuardianLookupService.lookUpChildOnce(asRequester, child);
        lookedUp++;
      } catch (RuntimeException e) {
        log.error(
            "Guardian lookup backfill stopped at the first failure, it resumes at the next"
                + " snapshot: position={}, {}",
            lookedUp + 1,
            RegisterFailure.of(e));
        break;
      }
      if (!pauseBetweenQueries()) {
        break;
      }
    }
    log.info(
        "Finished guardian lookup backfill: lookedUp={}, remaining={}",
        lookedUp,
        remaining.size() - lookedUp);
  }

  List<String> candidates() {
    return minorsNotLookedUp(candidates.firstSeenHoldersAndRecentThirdPillarPayouts().stream())
        .limit(MAX_PER_RUN)
        .toList();
  }

  List<String> backfillCandidates() {
    return minorsNotLookedUp(
            thirdPillarAnalytics.holdersInLatestSnapshot().stream()
                .map(ThirdPillarHolder::personalCode)
                .map(String::strip))
        .toList();
  }

  private Stream<String> minorsNotLookedUp(Stream<String> codes) {
    LocalDate today = LocalDate.now(clock);
    Set<String> lookedUp = Set.copyOf(guardianLookupRepository.findAllChildPersonalCodes());
    return codes
        .filter(PERSONAL_CODES::isValid)
        .filter(code -> PersonalCode.isMinor(code, today))
        .filter(not(lookedUp::contains))
        .distinct()
        .sorted();
  }

  private static void logFailure(RuntimeException e) {
    log.error(
        "Automatic guardian lookup failed, the child is asked again at the next event: {}",
        RegisterFailure.of(e));
  }

  private static void logSkipped() {
    log.info("Automatic guardian lookup skipped, no requester configured");
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
