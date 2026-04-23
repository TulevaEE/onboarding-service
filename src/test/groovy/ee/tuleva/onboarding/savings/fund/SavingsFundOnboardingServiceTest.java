package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.event.TrackableEventType.SAVINGS_FUND_ONBOARDING_STATUS_CHANGE;
import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.*;
import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.event.TrackableSystemEvent;
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

  @Test
  void whitelistLegalEntity_withNoExistingStatus_savesWhitelistedAndPublishesEvent() {
    given(savingsFundOnboardingRepository.findStatus("12345678", LEGAL_ENTITY))
        .willReturn(Optional.empty());

    savingsFundOnboardingService.whitelistLegalEntity("12345678", false);

    verify(savingsFundOnboardingRepository)
        .saveOnboardingStatus("12345678", LEGAL_ENTITY, WHITELISTED);

    var event = captureTrackableSystemEvent();
    assertThat(event.getType()).isEqualTo(SAVINGS_FUND_ONBOARDING_STATUS_CHANGE);
    assertThat(event.getData())
        .doesNotContainKey("oldStatus")
        .containsEntry("partyType", "LEGAL_ENTITY")
        .containsEntry("registryCode", "12345678")
        .containsEntry("newStatus", WHITELISTED)
        .containsEntry("outcome", "WHITELISTED")
        .containsEntry("override", false);
  }

  @Test
  void whitelistLegalEntity_withExistingNonWhitelistedStatusAndNoOverride_throwsConflict() {
    given(savingsFundOnboardingRepository.findStatus("12345678", LEGAL_ENTITY))
        .willReturn(Optional.of(REJECTED));

    assertThatThrownBy(() -> savingsFundOnboardingService.whitelistLegalEntity("12345678", false))
        .isInstanceOf(CompanyAlreadyHasOnboardingStatusException.class);

    verify(savingsFundOnboardingRepository, never()).saveOnboardingStatus(any(), any(), any());
    verify(eventPublisher, never()).publishEvent(any(TrackableSystemEvent.class));
  }

  @Test
  void
      whitelistLegalEntity_withExistingNonWhitelistedStatusAndOverrideTrue_savesAndEmitsOldStatus() {
    given(savingsFundOnboardingRepository.findStatus("12345678", LEGAL_ENTITY))
        .willReturn(Optional.of(REJECTED));

    savingsFundOnboardingService.whitelistLegalEntity("12345678", true);

    verify(savingsFundOnboardingRepository)
        .saveOnboardingStatus("12345678", LEGAL_ENTITY, WHITELISTED);

    assertThat(captureTrackableSystemEvent().getData())
        .containsEntry("oldStatus", REJECTED)
        .containsEntry("newStatus", WHITELISTED)
        .containsEntry("override", true);
  }

  @Test
  void whitelistLegalEntity_withExistingWhitelistedStatus_isNoOp() {
    given(savingsFundOnboardingRepository.findStatus("12345678", LEGAL_ENTITY))
        .willReturn(Optional.of(WHITELISTED));

    savingsFundOnboardingService.whitelistLegalEntity("12345678", false);

    verify(savingsFundOnboardingRepository, never()).saveOnboardingStatus(any(), any(), any());
    verify(eventPublisher, never()).publishEvent(any(TrackableSystemEvent.class));
  }

  private TrackableSystemEvent captureTrackableSystemEvent() {
    var captor = ArgumentCaptor.forClass(TrackableSystemEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    return captor.getValue();
  }
}
