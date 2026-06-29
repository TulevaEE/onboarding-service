package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.IN_REVIEW;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.VERIFIED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequestFixture.redemptionRequestFixture;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.aml.AmlCheck;
import ee.tuleva.onboarding.aml.AmlCheckType;
import ee.tuleva.onboarding.aml.AmlService;
import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.kyb.LegalEntityScreener;
import ee.tuleva.onboarding.kyc.survey.KycSurveyService;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingRepository;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus;
import ee.tuleva.onboarding.user.UserService;
import java.util.List;
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
  @Mock private KycSurveyService kycSurveyService;
  @Mock private AmlService amlService;
  @Mock private SavingsFundOnboardingRepository savingsFundOnboardingRepository;
  @Mock private LegalEntityScreener legalEntityScreener;

  @InjectMocks private RedemptionVerificationService service;

  @Test
  void process_personRequest_transitionsToVerifiedWhenAllAmlChecksPass() {
    var userId = 1L;
    var requestId = UUID.randomUUID();
    var request =
        redemptionRequestFixture()
            .id(requestId)
            .userId(userId)
            .partyId(new PartyId(PERSON, "38812121215"))
            .build();
    var user = sampleUser().id(userId).build();
    var country = new Country("EE");
    var passing =
        AmlCheck.builder()
            .personalCode(user.getPersonalCode())
            .type(AmlCheckType.SANCTION)
            .success(true)
            .build();

    given(userService.getByIdOrThrow(userId)).willReturn(user);
    given(kycSurveyService.getCountry(userId)).willReturn(Optional.of(country));
    given(amlService.addSanctionAndPepCheckIfMissing(user, country)).willReturn(List.of(passing));

    service.process(request);

    verify(redemptionStatusService).changeStatus(requestId, VERIFIED);
    verify(redemptionStatusService, never()).changeStatus(requestId, IN_REVIEW);
  }

  @Test
  void process_personRequest_transitionsToVerifiedWhenNoNewChecksNeeded() {
    var userId = 1L;
    var requestId = UUID.randomUUID();
    var request =
        redemptionRequestFixture()
            .id(requestId)
            .userId(userId)
            .partyId(new PartyId(PERSON, "38812121215"))
            .build();
    var user = sampleUser().id(userId).build();
    var country = new Country("EE");

    given(userService.getByIdOrThrow(userId)).willReturn(user);
    given(kycSurveyService.getCountry(userId)).willReturn(Optional.of(country));
    given(amlService.addSanctionAndPepCheckIfMissing(user, country)).willReturn(List.of());

    service.process(request);

    verify(redemptionStatusService).changeStatus(requestId, VERIFIED);
  }

  @Test
  void process_personRequest_transitionsToInReviewWhenAnyAmlCheckFails() {
    var userId = 1L;
    var requestId = UUID.randomUUID();
    var request =
        redemptionRequestFixture()
            .id(requestId)
            .userId(userId)
            .partyId(new PartyId(PERSON, "38812121215"))
            .build();
    var user = sampleUser().id(userId).build();
    var country = new Country("EE");
    var failing =
        AmlCheck.builder()
            .personalCode(user.getPersonalCode())
            .type(AmlCheckType.SANCTION)
            .success(false)
            .build();

    given(userService.getByIdOrThrow(userId)).willReturn(user);
    given(kycSurveyService.getCountry(userId)).willReturn(Optional.of(country));
    given(amlService.addSanctionAndPepCheckIfMissing(user, country)).willReturn(List.of(failing));

    service.process(request);

    verify(redemptionStatusService).changeStatus(requestId, IN_REVIEW);
    verify(redemptionStatusService, never()).changeStatus(requestId, VERIFIED);
  }

  @Test
  void process_personRequest_throwsWhenKycCountryMissing() {
    var userId = 1L;
    var request =
        redemptionRequestFixture()
            .userId(userId)
            .partyId(new PartyId(PERSON, "38812121215"))
            .build();
    var user = sampleUser().id(userId).build();

    given(userService.getByIdOrThrow(userId)).willReturn(user);
    given(kycSurveyService.getCountry(userId)).willReturn(Optional.empty());

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
        .partyId(new PartyId(LEGAL_ENTITY, registryCode))
        .build();
  }
}
