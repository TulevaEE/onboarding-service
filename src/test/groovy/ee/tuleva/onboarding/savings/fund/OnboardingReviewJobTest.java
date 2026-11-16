package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.aml.OnboardingReview.Outcome.COMPLETE;
import static ee.tuleva.onboarding.aml.OnboardingReview.Outcome.REJECT;
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import ee.tuleva.onboarding.aml.OnboardingReview;
import ee.tuleva.onboarding.aml.OnboardingReviews;
import ee.tuleva.onboarding.savings.SavingsFundOnboardingService;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OnboardingReviewJobTest {

  @Mock private OnboardingReviews onboardingReviews;
  @Mock private UserService userService;
  @Mock private SavingsFundOnboardingService savingsFundOnboardingService;
  @InjectMocks private OnboardingReviewJob job;

  private final User completed = sampleUser().id(1L).personalCode("38888888888").build();
  private final User rejected = sampleUser().id(2L).personalCode("48888888888").build();

  @Test
  void appliesEachSettledReviewToItsPersonsOnboarding() {
    given(onboardingReviews.settled())
        .willReturn(
            List.of(
                new OnboardingReview(completed.getPersonalCode(), COMPLETE),
                new OnboardingReview(rejected.getPersonalCode(), REJECT)));
    given(userService.findByPersonalCode(completed.getPersonalCode()))
        .willReturn(Optional.of(completed));
    given(userService.findByPersonalCode(rejected.getPersonalCode()))
        .willReturn(Optional.of(rejected));

    job.applySettledReviews();

    then(savingsFundOnboardingService).should().applyReview(completed, COMPLETE);
    then(savingsFundOnboardingService).should().applyReview(rejected, REJECT);
  }

  @Test
  void appliesTheOthersWhenOnePersonsFailsOrHasNoUser() {
    given(onboardingReviews.settled())
        .willReturn(
            List.of(
                new OnboardingReview("38888888899", COMPLETE),
                new OnboardingReview(completed.getPersonalCode(), COMPLETE),
                new OnboardingReview(rejected.getPersonalCode(), REJECT)));
    given(userService.findByPersonalCode("38888888899")).willReturn(Optional.empty());
    given(userService.findByPersonalCode(completed.getPersonalCode()))
        .willReturn(Optional.of(completed));
    given(userService.findByPersonalCode(rejected.getPersonalCode()))
        .willReturn(Optional.of(rejected));
    willThrow(new IllegalStateException("write failed"))
        .given(savingsFundOnboardingService)
        .applyReview(completed, COMPLETE);

    job.applySettledReviews();

    then(savingsFundOnboardingService).should().applyReview(rejected, REJECT);
  }

  @Test
  void appliesNothingWhenAmlServiceCannotAnswer() {
    given(onboardingReviews.settled()).willThrow(new IllegalStateException("aml-service is down"));

    job.applySettledReviews();

    then(savingsFundOnboardingService).should(never()).applyReview(completed, COMPLETE);
    then(userService).shouldHaveNoInteractions();
  }
}
