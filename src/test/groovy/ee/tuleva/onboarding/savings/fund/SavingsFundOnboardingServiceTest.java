package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.*;
import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.kyc.KycCheck;
import ee.tuleva.onboarding.user.User;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class SavingsFundOnboardingServiceTest {

  @Mock private SavingsFundOnboardingRepository savingsFundOnboardingRepository;
  @Mock private ApplicationEventPublisher eventPublisher;
  @InjectMocks private SavingsFundOnboardingService savingsFundOnboardingService;

  @Captor private ArgumentCaptor<Object> eventCaptor;

  User user = sampleUser().build();

  @Test
  void updateOnboardingStatusIfNeeded_publishesCompletedEventWhenStatusBecomesCompleted() {
    when(savingsFundOnboardingRepository.findStatusByPersonalCode(user.getPersonalCode()))
        .thenReturn(Optional.of(PENDING));
    var kycCheck = new KycCheck(LOW, Map.of());

    savingsFundOnboardingService.updateOnboardingStatusIfNeeded(user, kycCheck);

    verify(eventPublisher, atLeastOnce()).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getAllValues())
        .filteredOn(SavingsFundOnboardingCompletedEvent.class::isInstance)
        .singleElement()
        .isEqualTo(new SavingsFundOnboardingCompletedEvent(user));
  }

  @Test
  void updateOnboardingStatusIfNeeded_doesNotPublishCompletedEventForNonCompletedStatus() {
    when(savingsFundOnboardingRepository.findStatusByPersonalCode(user.getPersonalCode()))
        .thenReturn(Optional.empty());
    var kycCheck = new KycCheck(MEDIUM, Map.of());

    savingsFundOnboardingService.updateOnboardingStatusIfNeeded(user, kycCheck);

    verify(eventPublisher, never()).publishEvent(any(SavingsFundOnboardingCompletedEvent.class));
  }

  @Test
  void updateOnboardingStatusIfNeeded_doesNotPublishWhenAlreadyCompleted() {
    when(savingsFundOnboardingRepository.findStatusByPersonalCode(user.getPersonalCode()))
        .thenReturn(Optional.of(COMPLETED));
    var kycCheck = new KycCheck(LOW, Map.of());

    savingsFundOnboardingService.updateOnboardingStatusIfNeeded(user, kycCheck);

    verify(eventPublisher, never()).publishEvent(any(SavingsFundOnboardingCompletedEvent.class));
  }
}
