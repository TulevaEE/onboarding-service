package ee.tuleva.onboarding.aml.risklevel;

import ee.tuleva.onboarding.aml.OnboardingReview;
import ee.tuleva.onboarding.aml.OnboardingReviews;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile({"dev", "staging"})
class DevOnboardingReviews implements OnboardingReviews {

  @Override
  public List<OnboardingReview> settled() {
    return List.of();
  }
}
