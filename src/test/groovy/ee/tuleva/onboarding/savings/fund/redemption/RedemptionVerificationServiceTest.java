package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.AML;
import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.IN_REVIEW;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.VERIFIED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequestFixture.redemptionRequestFixture;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.aml.AmlService;
import ee.tuleva.onboarding.aml.RiskLevels;
import ee.tuleva.onboarding.country.Countries;
import ee.tuleva.onboarding.kyb.LegalEntityScreener;
import ee.tuleva.onboarding.kyc.KycCountryService;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingRepository;
import ee.tuleva.onboarding.user.UserService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RedemptionVerificationServiceTest {

  @Mock private RedemptionStatusService redemptionStatusService;
  @Mock private UserService userService;
  @Mock private KycCountryService kycCountryService;
  @Mock private AmlService amlService;
  @Mock private RiskLevels riskLevels;
  @Mock private SavingsFundOnboardingRepository savingsFundOnboardingRepository;
  @Mock private LegalEntityScreener legalEntityScreener;
  @Mock private OperationsNotificationService notificationService;

  @InjectMocks private RedemptionVerificationService service;

  @Test
  void process_personRequest_transitionsToVerifiedWhenScreeningClearAndNotHighRisk() {
    var userId = 1L;
    var requestId = UUID.randomUUID();
    var request =
        redemptionRequestFixture()
            .id(requestId)
            .userId(userId)
            .partyType(PERSON)
            .partyCode("38812121215")
            .build();
    var user = sampleUser().id(userId).build();
    var countries = Countries.of("EE");

    given(userService.findByPersonalCode("38812121215")).willReturn(Optional.of(user));
    given(kycCountryService.getCountries(userId)).willReturn(Optional.of(countries));
    given(amlService.isSanctionAndPepClear(user, countries)).willReturn(true);
    given(riskLevels.isHighRisk(user.getPersonalCode())).willReturn(false);

    service.process(request);

    verify(redemptionStatusService).changeStatus(requestId, VERIFIED);
    verify(redemptionStatusService, never()).changeStatus(requestId, IN_REVIEW);
  }

  @Test
  void process_personRequest_transitionsToInReviewWhenScreeningNotClear() {
    var userId = 1L;
    var requestId = UUID.randomUUID();
    var request =
        redemptionRequestFixture()
            .id(requestId)
            .userId(userId)
            .partyType(PERSON)
            .partyCode("38812121215")
            .build();
    var user = sampleUser().id(userId).build();
    var countries = Countries.of("EE");

    given(userService.findByPersonalCode("38812121215")).willReturn(Optional.of(user));
    given(kycCountryService.getCountries(userId)).willReturn(Optional.of(countries));
    given(amlService.isSanctionAndPepClear(user, countries)).willReturn(false);

    service.process(request);

    verify(redemptionStatusService).changeStatus(requestId, IN_REVIEW);
    verify(redemptionStatusService, never()).changeStatus(requestId, VERIFIED);
    verify(notificationService)
        .sendMessage(
            "AML: redemption held for review: id=" + requestId + ", amount=10.00 EUR", AML);
  }

  @Test
  void process_personRequest_holdsRedemptionEvenWhenNotificationFails() {
    var userId = 1L;
    var requestId = UUID.randomUUID();
    var request =
        redemptionRequestFixture()
            .id(requestId)
            .userId(userId)
            .partyType(PERSON)
            .partyCode("38812121215")
            .build();
    var user = sampleUser().id(userId).build();
    var countries = Countries.of("EE");

    given(userService.findByPersonalCode("38812121215")).willReturn(Optional.of(user));
    given(kycCountryService.getCountries(userId)).willReturn(Optional.of(countries));
    given(amlService.isSanctionAndPepClear(user, countries)).willReturn(false);
    willThrow(new IllegalStateException("Slack unavailable"))
        .given(notificationService)
        .sendMessage(anyString(), any());

    service.process(request);

    verify(redemptionStatusService).changeStatus(requestId, IN_REVIEW);
    verify(redemptionStatusService, never()).changeStatus(requestId, VERIFIED);
  }

  @Test
  void process_personRequest_transitionsToInReviewWhenPartyIsHighRisk() {
    var userId = 1L;
    var requestId = UUID.randomUUID();
    var request =
        redemptionRequestFixture()
            .id(requestId)
            .userId(userId)
            .partyType(PERSON)
            .partyCode("38812121215")
            .build();
    var user = sampleUser().id(userId).build();
    var countries = Countries.of("EE");

    given(userService.findByPersonalCode("38812121215")).willReturn(Optional.of(user));
    given(kycCountryService.getCountries(userId)).willReturn(Optional.of(countries));
    given(amlService.isSanctionAndPepClear(user, countries)).willReturn(true);
    given(riskLevels.isHighRisk(user.getPersonalCode())).willReturn(true);

    service.process(request);

    verify(redemptionStatusService).changeStatus(requestId, IN_REVIEW);
    verify(redemptionStatusService, never()).changeStatus(requestId, VERIFIED);
  }

  @Test
  void process_personRequest_screensThePartyNotTheActor() {
    var actorUserId = 1L;
    var childCode = "61506150006";
    var requestId = UUID.randomUUID();
    var request =
        redemptionRequestFixture()
            .id(requestId)
            .userId(actorUserId)
            .partyType(PERSON)
            .partyCode(childCode)
            .build();
    var child = sampleUser().id(2L).personalCode(childCode).build();
    var countries = Countries.of("EE");

    given(userService.findByPersonalCode(childCode)).willReturn(Optional.of(child));
    given(kycCountryService.getCountries(child.getId())).willReturn(Optional.of(countries));
    given(amlService.isSanctionAndPepClear(child, countries)).willReturn(true);
    given(riskLevels.isHighRisk(childCode)).willReturn(false);

    service.process(request);

    verify(redemptionStatusService).changeStatus(requestId, VERIFIED);
  }

  @Test
  void process_personRequest_throwsWhenKycCountryMissing() {
    var userId = 1L;
    var request =
        redemptionRequestFixture()
            .userId(userId)
            .partyType(PERSON)
            .partyCode("38812121215")
            .build();
    var user = sampleUser().id(userId).build();

    given(userService.findByPersonalCode("38812121215")).willReturn(Optional.of(user));
    given(kycCountryService.getCountries(userId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.process(request)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void process_personRequest_throwsWhenPartyUserNotFound() {
    var request =
        redemptionRequestFixture().userId(1L).partyType(PERSON).partyCode("61506150006").build();

    given(userService.findByPersonalCode("61506150006")).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.process(request)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void process_legalEntityRequest_transitionsToVerifiedWhenLatestKybCompleted() {
    var registryCode = "16001234";
    var requestId = UUID.randomUUID();
    var request = legalEntityRequest(requestId, registryCode);

    given(savingsFundOnboardingRepository.isOnboardingCompleted(registryCode, LEGAL_ENTITY))
        .willReturn(true);

    service.process(request);

    verify(redemptionStatusService).changeStatus(requestId, VERIFIED);
    verify(redemptionStatusService, never()).changeStatus(requestId, IN_REVIEW);
    verify(legalEntityScreener, never()).screenLatest(registryCode);
  }

  @Test
  void process_legalEntityRequest_transitionsToInReviewWhenLatestKybRejected() {
    var registryCode = "16001234";
    var requestId = UUID.randomUUID();
    var request = legalEntityRequest(requestId, registryCode);

    given(savingsFundOnboardingRepository.isOnboardingCompleted(registryCode, LEGAL_ENTITY))
        .willReturn(false);
    given(savingsFundOnboardingRepository.findStatus(registryCode, LEGAL_ENTITY))
        .willReturn(Optional.of(SavingsFundOnboardingStatus.REJECTED));

    service.process(request);

    verify(redemptionStatusService).changeStatus(requestId, IN_REVIEW);
    verify(redemptionStatusService, never()).changeStatus(requestId, VERIFIED);
    verify(legalEntityScreener, never()).screenLatest(registryCode);
  }

  @Test
  void process_legalEntityRequest_reScreensWhenStatusMissingThenVerifiesIfCompleted() {
    var registryCode = "16001234";
    var requestId = UUID.randomUUID();
    var request = legalEntityRequest(requestId, registryCode);

    given(savingsFundOnboardingRepository.isOnboardingCompleted(registryCode, LEGAL_ENTITY))
        .willReturn(false, true);
    given(savingsFundOnboardingRepository.findStatus(registryCode, LEGAL_ENTITY))
        .willReturn(Optional.empty());

    service.process(request);

    verify(legalEntityScreener).screenLatest(registryCode);
    verify(redemptionStatusService).changeStatus(requestId, VERIFIED);
  }

  @Test
  void process_legalEntityRequest_reScreensWhenStatusPendingThenInReviewIfRejected() {
    var registryCode = "16001234";
    var requestId = UUID.randomUUID();
    var request = legalEntityRequest(requestId, registryCode);

    given(savingsFundOnboardingRepository.isOnboardingCompleted(registryCode, LEGAL_ENTITY))
        .willReturn(false, false);
    given(savingsFundOnboardingRepository.findStatus(registryCode, LEGAL_ENTITY))
        .willReturn(Optional.of(SavingsFundOnboardingStatus.PENDING));

    service.process(request);

    verify(legalEntityScreener).screenLatest(registryCode);
    verify(redemptionStatusService).changeStatus(requestId, IN_REVIEW);
  }

  @Test
  void process_legalEntityRequest_routesToInReviewWhenStatusStillMissingAfterReScreen() {
    var registryCode = "16001234";
    var requestId = UUID.randomUUID();
    var request = legalEntityRequest(requestId, registryCode);

    given(savingsFundOnboardingRepository.isOnboardingCompleted(registryCode, LEGAL_ENTITY))
        .willReturn(false, false);
    given(savingsFundOnboardingRepository.findStatus(registryCode, LEGAL_ENTITY))
        .willReturn(Optional.empty());

    service.process(request);

    verify(legalEntityScreener).screenLatest(registryCode);
    verify(redemptionStatusService).changeStatus(requestId, IN_REVIEW);
  }

  @Test
  void process_legalEntityRequest_routesToInReviewWhenScreenLatestThrows() {
    var registryCode = "16001234";
    var requestId = UUID.randomUUID();
    var request = legalEntityRequest(requestId, registryCode);

    given(savingsFundOnboardingRepository.isOnboardingCompleted(registryCode, LEGAL_ENTITY))
        .willReturn(false);
    given(savingsFundOnboardingRepository.findStatus(registryCode, LEGAL_ENTITY))
        .willReturn(Optional.empty());
    willThrow(new IllegalStateException("Ariregister unavailable"))
        .given(legalEntityScreener)
        .screenLatest(registryCode);

    service.process(request);

    verify(redemptionStatusService).changeStatus(requestId, IN_REVIEW);
    verify(redemptionStatusService, never()).changeStatus(requestId, VERIFIED);
  }

  private static RedemptionRequest legalEntityRequest(UUID requestId, String registryCode) {
    return redemptionRequestFixture()
        .id(requestId)
        .partyType(LEGAL_ENTITY)
        .partyCode(registryCode)
        .build();
  }

  @Test
  void process_personRequest_screensAgainstCitizenshipsTheSurveyDoesNotCarry() {
    var userId = 1L;
    var requestId = UUID.randomUUID();
    var request =
        redemptionRequestFixture()
            .id(requestId)
            .userId(userId)
            .partyType(PERSON)
            .partyCode("38812121215")
            .build();
    var user = sampleUser().id(userId).build();

    given(userService.findByPersonalCode("38812121215")).willReturn(Optional.of(user));
    given(kycCountryService.getCountries(userId)).willReturn(Optional.of(Countries.of("EE")));
    given(amlService.recordedCitizenships(user)).willReturn(Countries.of("RU"));
    given(amlService.isSanctionAndPepClear(user, Countries.of("EE", "RU"))).willReturn(true);
    given(riskLevels.isHighRisk(user.getPersonalCode())).willReturn(false);

    service.process(request);

    verify(amlService).isSanctionAndPepClear(user, Countries.of("EE", "RU"));
    verify(redemptionStatusService).changeStatus(requestId, VERIFIED);
  }
}
