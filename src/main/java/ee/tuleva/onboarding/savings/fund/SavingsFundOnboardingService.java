package ee.tuleva.onboarding.savings.fund;

import ee.tuleva.onboarding.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SavingsFundOnboardingService {
  private final SavingsFundOnboardingRepository savingsFundOnboardingRepository;

  public boolean isOnboardingCompleted(User user) {
    return savingsFundOnboardingRepository.isOnboardingCompleted(user.getId());
  }

  public SavingsFundOnboardingStatus getOnboardingStatus(User user) {
    if (isOnboardingCompleted(user)) {
      return SavingsFundOnboardingStatus.COMPLETED;
    }
    return SavingsFundOnboardingStatus.NOT_STARTED;
  }
}
