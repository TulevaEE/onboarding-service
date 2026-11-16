package ee.tuleva.onboarding.savings.fund;

import ee.tuleva.onboarding.aml.OnboardingReview;
import ee.tuleva.onboarding.aml.OnboardingReviews;
import ee.tuleva.onboarding.savings.SavingsFundOnboardingService;
import ee.tuleva.onboarding.user.UserService;
import java.util.List;
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

  @Scheduled(cron = "0 */5 * * * *", zone = "Europe/Tallinn")
  @SchedulerLock(name = "OnboardingReviewJob_applySettledReviews", lockAtMostFor = "4m")
  public void applySettledReviews() {
    List<OnboardingReview> settled;
    try {
      settled = onboardingReviews.settled();
    } catch (Exception e) {
      log.error("Failed to read the settled onboarding reviews, applying none this time", e);
      return;
    }
    settled.forEach(this::apply);
  }

  private void apply(OnboardingReview review) {
    userService
        .findByPersonalCode(review.personalCode())
        .ifPresentOrElse(
            user -> {
              try {
                savingsFundOnboardingService.applyReview(user, review.outcome());
              } catch (Exception e) {
                log.error(
                    "Failed to apply an onboarding review: userId={}, outcome={}",
                    user.getId(),
                    review.outcome(),
                    e);
              }
            },
            () ->
                log.error(
                    "Onboarding review names no user, applying it later: outcome={}",
                    review.outcome()));
  }
}
