package ee.tuleva.onboarding.savings;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.event.TrackableEventType.SAVINGS_FUND_ONBOARDING_STATUS_CHANGE;
import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.*;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.event.TrackableEvent;
import ee.tuleva.onboarding.kyc.KycCheck;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingCompletedEvent;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingRepository;
import ee.tuleva.onboarding.user.User;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class SavingsFundOnboardingServiceTest {

  @Mock private SavingsFundOnboardingRepository savingsFundOnboardingRepository;
  @Mock private ApplicationEventPublisher eventPublisher;
  @InjectMocks private SavingsFundOnboardingService savingsFundOnboardingService;

  User user = sampleUser().build();

  @Test
  void updateOnboardingStatusIfNeeded_publishesCompletedEventOnceWhenStatusBecomesCompleted() {
    given(savingsFundOnboardingRepository.findStatus(user.getPersonalCode(), PERSON))
        .willReturn(Optional.of(PENDING));
    given(
            savingsFundOnboardingRepository.saveOnboardingStatus(
                user.getPersonalCode(), PERSON, COMPLETED))
        .willReturn(Optional.of(PENDING));
    var kycCheck = new KycCheck(LOW, Map.of());

    savingsFundOnboardingService.updateOnboardingStatusIfNeeded(user, kycCheck);

    verify(eventPublisher).publishEvent(new SavingsFundOnboardingCompletedEvent(user));
    verify(eventPublisher)
        .publishEvent(
            new TrackableEvent(
                user,
                SAVINGS_FUND_ONBOARDING_STATUS_CHANGE,
                Map.of("oldStatus", PENDING, "newStatus", COMPLETED)));
  }

  @Test
  void updateOnboardingStatusIfNeeded_publishesNothingWhenWriteFoundAlreadyCompleted() {
    given(savingsFundOnboardingRepository.findStatus(user.getPersonalCode(), PERSON))
        .willReturn(Optional.of(PENDING));
    given(
            savingsFundOnboardingRepository.saveOnboardingStatus(
                user.getPersonalCode(), PERSON, COMPLETED))
        .willReturn(Optional.of(COMPLETED));
    var kycCheck = new KycCheck(LOW, Map.of());

    savingsFundOnboardingService.updateOnboardingStatusIfNeeded(user, kycCheck);

    verify(eventPublisher, never()).publishEvent(any(SavingsFundOnboardingCompletedEvent.class));
    verify(eventPublisher, never()).publishEvent(any(TrackableEvent.class));
  }

  @Test
  void updateOnboardingStatusIfNeeded_recordsAbsentOldStatusWhenPartyWasNotOnboardedBefore() {
    given(savingsFundOnboardingRepository.findStatus(user.getPersonalCode(), PERSON))
        .willReturn(Optional.empty());
    given(
            savingsFundOnboardingRepository.saveOnboardingStatus(
                user.getPersonalCode(), PERSON, COMPLETED))
        .willReturn(Optional.empty());
    var kycCheck = new KycCheck(NONE, Map.of());

    savingsFundOnboardingService.updateOnboardingStatusIfNeeded(user, kycCheck);

    verify(eventPublisher).publishEvent(new SavingsFundOnboardingCompletedEvent(user));
    verify(eventPublisher)
        .publishEvent(
            new TrackableEvent(
                user, SAVINGS_FUND_ONBOARDING_STATUS_CHANGE, Map.of("newStatus", COMPLETED)));
  }

  @Test
  void updateOnboardingStatusIfNeeded_doesNotPublishCompletedEventForNonCompletedStatus() {
    given(savingsFundOnboardingRepository.findStatus(user.getPersonalCode(), PERSON))
        .willReturn(Optional.empty());
    var kycCheck = new KycCheck(MEDIUM, Map.of());

    savingsFundOnboardingService.updateOnboardingStatusIfNeeded(user, kycCheck);

    verify(eventPublisher, never()).publishEvent(any(SavingsFundOnboardingCompletedEvent.class));
  }

  @Test
  void updateOnboardingStatusIfNeeded_doesNotPublishWhenAlreadyCompleted() {
    given(savingsFundOnboardingRepository.findStatus(user.getPersonalCode(), PERSON))
        .willReturn(Optional.of(COMPLETED));
    var kycCheck = new KycCheck(LOW, Map.of());

    savingsFundOnboardingService.updateOnboardingStatusIfNeeded(user, kycCheck);

    verify(eventPublisher, never()).publishEvent(any(SavingsFundOnboardingCompletedEvent.class));
  }

  @Test
  void isOnboardingCompleted_delegatesToRepository() {
    given(savingsFundOnboardingRepository.isOnboardingCompleted("38501010001", PERSON))
        .willReturn(true);

    assertThat(savingsFundOnboardingService.isOnboardingCompleted("38501010001", PERSON)).isTrue();
  }

  @Test
  void getOnboardingStatus_delegatesToRepository() {
    given(savingsFundOnboardingRepository.findStatus("38501010001", PERSON))
        .willReturn(Optional.of(COMPLETED));

    assertThat(savingsFundOnboardingService.getOnboardingStatus(new PartyId(PERSON, "38501010001")))
        .isEqualTo(COMPLETED);
  }

  @Test
  void seedPersonOnboardingIfAbsent_insertsPendingIfAbsent() {
    savingsFundOnboardingService.seedPersonOnboardingIfAbsent("60001019906");

    verify(savingsFundOnboardingRepository)
        .insertOnboardingStatusIfAbsent("60001019906", PERSON, PENDING);
  }
}
