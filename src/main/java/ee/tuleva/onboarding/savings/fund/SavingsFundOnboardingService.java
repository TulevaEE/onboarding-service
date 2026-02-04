package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.event.TrackableEventType.SAVINGS_FUND_ONBOARDING_STATUS_CHANGE;
import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.*;

import ee.tuleva.onboarding.event.TrackableEvent;
import ee.tuleva.onboarding.kyc.KycCheck;
import ee.tuleva.onboarding.kyc.KycCheck.RiskLevel;
import ee.tuleva.onboarding.user.User;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SavingsFundOnboardingService {

  private final SavingsFundOnboardingRepository savingsFundOnboardingRepository;
  private final ApplicationEventPublisher eventPublisher;

  public boolean isOnboardingCompleted(User user) {
    return savingsFundOnboardingRepository.isOnboardingCompleted(user.getPersonalCode());
  }

  public SavingsFundOnboardingStatus getOnboardingStatus(User user) {
    return savingsFundOnboardingRepository
        .findStatusByPersonalCode(user.getPersonalCode())
        .orElse(null);
  }

  public void updateOnboardingStatusIfNeeded(User user, KycCheck kycCheck) {
    SavingsFundOnboardingStatus oldStatus =
        savingsFundOnboardingRepository
            .findStatusByPersonalCode(user.getPersonalCode())
            .orElse(null);
    if (oldStatus == COMPLETED) {
      return;
    }
    SavingsFundOnboardingStatus newStatus = mapRiskLevelToStatus(kycCheck.riskLevel());
    if (newStatus == oldStatus) {
      return;
    }
    savingsFundOnboardingRepository.saveOnboardingStatus(user.getPersonalCode(), newStatus);
    Map<String, Object> eventData = new HashMap<>();
    if (oldStatus != null) {
      eventData.put("oldStatus", oldStatus);
    }
    eventData.put("newStatus", newStatus);
    eventPublisher.publishEvent(
        new TrackableEvent(user, SAVINGS_FUND_ONBOARDING_STATUS_CHANGE, eventData));
  }

  private SavingsFundOnboardingStatus mapRiskLevelToStatus(RiskLevel riskLevel) {
    return switch (riskLevel) {
      case LOW, NONE -> COMPLETED;
      case MEDIUM -> PENDING;
      case HIGH -> REJECTED;
    };
  }
}
