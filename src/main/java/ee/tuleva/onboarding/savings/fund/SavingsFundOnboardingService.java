package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.event.TrackableEventType.SAVINGS_FUND_ONBOARDING_STATUS_CHANGE;
import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.*;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.event.TrackableEvent;
import ee.tuleva.onboarding.kyc.KycCheck;
import ee.tuleva.onboarding.kyc.KycCheck.RiskLevel;
import ee.tuleva.onboarding.user.User;
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

  public boolean isWhitelisted(Person person) {
    return savingsFundOnboardingRepository
        .findStatusByPersonalCode(person.getPersonalCode())
        .isPresent();
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
            .orElseThrow();
    if (oldStatus == COMPLETED) {
      return;
    }
    SavingsFundOnboardingStatus newStatus = mapRiskLevelToStatus(kycCheck.riskLevel());
    if (oldStatus == newStatus) {
      return;
    }
    savingsFundOnboardingRepository.saveOnboardingStatus(user.getPersonalCode(), newStatus);
    eventPublisher.publishEvent(
        new TrackableEvent(
            user,
            SAVINGS_FUND_ONBOARDING_STATUS_CHANGE,
            Map.of("oldStatus", oldStatus, "newStatus", newStatus)));
  }

  private SavingsFundOnboardingStatus mapRiskLevelToStatus(RiskLevel riskLevel) {
    return switch (riskLevel) {
      case LOW, NONE -> COMPLETED;
      case MEDIUM -> PENDING;
      case HIGH -> REJECTED;
    };
  }
}
