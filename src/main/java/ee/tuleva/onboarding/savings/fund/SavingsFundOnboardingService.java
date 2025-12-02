package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.*;

import ee.tuleva.onboarding.kyc.KycCheck;
import ee.tuleva.onboarding.kyc.KycCheck.RiskLevel;
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
    return savingsFundOnboardingRepository.findStatusByUserId(user.getId()).orElse(NOT_STARTED);
  }

  public void updateOnboardingStatusIfNeeded(Long userId, KycCheck kycCheck) {
    var currentStatus = savingsFundOnboardingRepository.findStatusByUserId(userId);
    if (currentStatus.isPresent() && currentStatus.get() == COMPLETED) {
      return;
    }
    SavingsFundOnboardingStatus newStatus = mapRiskLevelToStatus(kycCheck.riskLevel());
    savingsFundOnboardingRepository.saveOnboardingStatus(userId, newStatus);
  }

  private SavingsFundOnboardingStatus mapRiskLevelToStatus(RiskLevel riskLevel) {
    return switch (riskLevel) {
      case LOW -> COMPLETED;
      case MEDIUM -> PENDING;
      case HIGH -> REJECTED;
    };
  }
}
