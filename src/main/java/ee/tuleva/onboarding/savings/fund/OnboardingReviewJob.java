package ee.tuleva.onboarding.savings.fund;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

import ee.tuleva.onboarding.aml.OnboardingReview;
import ee.tuleva.onboarding.aml.OnboardingReview.PartyType;
import ee.tuleva.onboarding.aml.OnboardingReviews;
import ee.tuleva.onboarding.savings.SavingsFundOnboardingService;
import ee.tuleva.onboarding.user.UserService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class OnboardingReviewJob {

  private final OnboardingReviews onboardingReviews;
  private final UserService userService;
  private final SavingsFundOnboardingService savingsFundOnboardingService;
  private final LegalEntitySavingsFundOnboardingService legalEntityOnboardingService;

  @Scheduled(cron = "0 */5 * * * *", zone = "Europe/Tallinn")
  @SchedulerLock(
      name = "OnboardingReviewJob_applySettledReviews",
      lockAtMostFor = "4m",
      lockAtLeastFor = "1m")
  public void applySettledReviews() {
    List<OnboardingReview> settled;
    try {
      settled = onboardingReviews.settled();
    } catch (Exception e) {
      log.error("Failed to read the settled onboarding reviews, applying none this time", e);
      return;
    }
    if (settled.isEmpty()) {
      return;
    }
    Map<Applied, Long> counts =
        settled.stream().map(this::apply).collect(groupingBy(identity(), counting()));
    log.info(
        "Applied the settled onboarding reviews: applied={}, noUser={}, failed={}",
        counts.getOrDefault(Applied.APPLIED, 0L),
        counts.getOrDefault(Applied.NO_USER, 0L),
        counts.getOrDefault(Applied.FAILED, 0L));
  }

  private Applied applyToCompany(OnboardingReview review) {
    try {
      legalEntityOnboardingService.applyReview(review.code(), review.outcome());
      return Applied.APPLIED;
    } catch (Exception e) {
      log.error(
          "Failed to apply an onboarding review: registryCode={}, outcome={}",
          review.code(),
          review.outcome(),
          e);
      return Applied.FAILED;
    }
  }

  private enum Applied {
    APPLIED,
    NO_USER,
    FAILED
  }

  private Applied apply(OnboardingReview review) {
    if (review.partyType() == PartyType.LEGAL_ENTITY) {
      return applyToCompany(review);
    }
    return userService
        .findByPersonalCode(review.code())
        .map(
            user -> {
              try {
                savingsFundOnboardingService.applyReview(user, review.outcome());
                return Applied.APPLIED;
              } catch (Exception e) {
                log.error(
                    "Failed to apply an onboarding review: userId={}, outcome={}",
                    user.getId(),
                    review.outcome(),
                    e);
                return Applied.FAILED;
              }
            })
        .orElse(Applied.NO_USER);
  }
}
