package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.*;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.kyc.KycCheck;
import ee.tuleva.onboarding.party.PartyId;
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
    when(savingsFundOnboardingRepository.findStatus(user.getPersonalCode(), PERSON))
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
    when(savingsFundOnboardingRepository.findStatus(user.getPersonalCode(), PERSON))
        .thenReturn(Optional.empty());
    var kycCheck = new KycCheck(MEDIUM, Map.of());

    savingsFundOnboardingService.updateOnboardingStatusIfNeeded(user, kycCheck);

    verify(eventPublisher, never()).publishEvent(any(SavingsFundOnboardingCompletedEvent.class));
  }

  @Test
  void updateOnboardingStatusIfNeeded_doesNotPublishWhenAlreadyCompleted() {
    when(savingsFundOnboardingRepository.findStatus(user.getPersonalCode(), PERSON))
        .thenReturn(Optional.of(COMPLETED));
    var kycCheck = new KycCheck(LOW, Map.of());

    savingsFundOnboardingService.updateOnboardingStatusIfNeeded(user, kycCheck);

    verify(eventPublisher, never()).publishEvent(any(SavingsFundOnboardingCompletedEvent.class));
  }

  @Test
  void isOnboardingCompleted_delegatesToRepository() {
    when(savingsFundOnboardingRepository.isOnboardingCompleted("38501010001", PERSON))
        .thenReturn(true);

    assertThat(savingsFundOnboardingService.isOnboardingCompleted("38501010001", PERSON)).isTrue();
  }

  @Test
  void getOnboardingStatus_delegatesToRepository() {
    when(savingsFundOnboardingRepository.findStatus("38501010001", PERSON))
        .thenReturn(Optional.of(COMPLETED));

    assertThat(savingsFundOnboardingService.getOnboardingStatus(new PartyId(PERSON, "38501010001")))
        .isEqualTo(COMPLETED);
  }
}
