package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.PENDING;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.REJECTED;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

import ee.tuleva.onboarding.kyc.KycCheckService;
import ee.tuleva.onboarding.savings.SavingsFundOnboardingService;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class OnboardingRuleRerunJob {

  static final LocalDate RERUN_ON = LocalDate.of(2026, 11, 18);

  static final Instant REJECTED_BY_THE_EARLIER_RULE_BEFORE = Instant.parse("2026-11-16T22:00:00Z");

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  private final SavingsFundOnboardingRepository savingsFundOnboardingRepository;
  private final UserService userService;
  private final SavingsFundOnboardingService savingsFundOnboardingService;
  private final KycCheckService kycCheckService;
  private final Clock clock;

  @Scheduled(cron = "0 0 20 * * *", zone = "Europe/Tallinn")
  @SchedulerLock(name = "OnboardingRuleRerunJob_rerun", lockAtMostFor = "1h", lockAtLeastFor = "5m")
  public void rerun() {
    if (!LocalDate.now(clock.withZone(TALLINN)).equals(RERUN_ON)) {
      return;
    }
    Map<Rerun, Long> held =
        forEachUser(
            savingsFundOnboardingRepository.findPersonCodes(
                REJECTED, REJECTED_BY_THE_EARLIER_RULE_BEFORE),
            user ->
                savingsFundOnboardingService.holdForReview(
                    user, REJECTED_BY_THE_EARLIER_RULE_BEFORE));
    Map<Rerun, Long> assessed =
        forEachUser(
            savingsFundOnboardingRepository.findPersonCodes(PENDING, clock.instant()),
            kycCheckService::checkLatestOnboardingSurvey);
    log.info(
        "Re-ran the savings fund onboarding rule for existing onboardings: rejected={},"
            + " pending={}",
        held,
        assessed);
  }

  enum Rerun {
    DONE,
    UNCHANGED,
    NO_USER,
    FAILED
  }

  private Map<Rerun, Long> forEachUser(List<String> personalCodes, Predicate<User> action) {
    return personalCodes.stream()
        .map(personalCode -> rerun(personalCode, action))
        .collect(groupingBy(identity(), () -> new EnumMap<>(Rerun.class), counting()));
  }

  private Rerun rerun(String personalCode, Predicate<User> action) {
    return userService
        .findByPersonalCode(personalCode)
        .map(
            user -> {
              try {
                return action.test(user) ? Rerun.DONE : Rerun.UNCHANGED;
              } catch (Exception e) {
                log.error(
                    "Failed to re-run the savings fund onboarding rule: userId={}",
                    user.getId(),
                    e);
                return Rerun.FAILED;
              }
            })
        .orElse(Rerun.NO_USER);
  }
}
