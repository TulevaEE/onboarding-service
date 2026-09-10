package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.aml.ScreeningOutcome.CLEAR;
import static ee.tuleva.onboarding.aml.ScreeningOutcome.PEP_HIT;
import static ee.tuleva.onboarding.aml.ScreeningOutcome.SANCTION_HIT;
import static ee.tuleva.onboarding.aml.ScreeningOutcome.UNAVAILABLE;
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.kyb.KybCheckType.COMPANY_ACTIVE;
import static ee.tuleva.onboarding.kyb.KybCheckType.COMPANY_SANCTION;
import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionHoldService.SYSTEM;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.VERIFIED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequestFixture.redemptionRequestFixture;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerificationService.HIGH_RISK;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerificationService.KYB_NOT_COMPLETED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerificationService.KYB_SCREENING_FAILED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerificationService.PEP;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerificationService.SANCTION;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.aml.RiskLevels;
import ee.tuleva.onboarding.aml.SanctionAndPepScreener;
import ee.tuleva.onboarding.country.Countries;
import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.LegalEntityScreener;
import ee.tuleva.onboarding.kyc.KycCountryService;
import ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingRepository;
import ee.tuleva.onboarding.user.UserService;
import java.util.List;
import java.util.Map;
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
  @Mock private RedemptionHoldService holdService;
  @Mock private UserService userService;
  @Mock private KycCountryService kycCountryService;
  @Mock private SanctionAndPepScreener sanctionAndPepScreener;
  @Mock private RiskLevels riskLevels;
  @Mock private SavingsFundOnboardingRepository savingsFundOnboardingRepository;
  @Mock private LegalEntityScreener legalEntityScreener;

  @InjectMocks private RedemptionVerificationService service;

  @Test
  void process_personRequest_verifiesWhenScreeningClearAndNotHighRisk() {
    var userId = 1L;
    var requestId = UUID.randomUUID();
    var request = personRequest(requestId, userId, "38812121215");
    var user = sampleUser().id(userId).build();
    var countries = Countries.of("EE");

    given(userService.findByPersonalCode("38812121215")).willReturn(Optional.of(user));
    given(kycCountryService.getCountries(userId)).willReturn(Optional.of(countries));
    given(sanctionAndPepScreener.screeningOutcome(user, countries)).willReturn(CLEAR);
    given(riskLevels.isHighRisk(user.getPersonalCode())).willReturn(false);

    service.process(request);

    verify(redemptionStatusService).changeStatus(requestId, VERIFIED);
    verifyNoInteractions(holdService);
  }

  @Test
  void process_personRequest_freezesOnSanctionsHit() {
    var userId = 1L;
    var requestId = UUID.randomUUID();
    var request = personRequest(requestId, userId, "38812121215");
    var user = sampleUser().id(userId).build();
    var countries = Countries.of("EE");

    given(userService.findByPersonalCode("38812121215")).willReturn(Optional.of(user));
    given(kycCountryService.getCountries(userId)).willReturn(Optional.of(countries));
    given(sanctionAndPepScreener.screeningOutcome(user, countries)).willReturn(SANCTION_HIT);

    service.process(request);

    verify(holdService).freeze(requestId, SANCTION);
    verify(holdService, never()).holdPayout(any(), any(), any());
    verify(redemptionStatusService, never()).changeStatus(any(), any());
    verify(riskLevels, never()).isHighRisk(any());
  }

  @Test
  void process_personRequest_verifiesButHoldsPayoutOnPepHit() {
    var userId = 1L;
    var requestId = UUID.randomUUID();
    var request = personRequest(requestId, userId, "38812121215");
    var user = sampleUser().id(userId).build();
    var countries = Countries.of("EE");

    given(userService.findByPersonalCode("38812121215")).willReturn(Optional.of(user));
    given(kycCountryService.getCountries(userId)).willReturn(Optional.of(countries));
    given(sanctionAndPepScreener.screeningOutcome(user, countries)).willReturn(PEP_HIT);
    given(riskLevels.isHighRisk(user.getPersonalCode())).willReturn(false);

    service.process(request);

    verify(holdService).holdPayout(requestId, PEP, SYSTEM);
    verify(redemptionStatusService).changeStatus(requestId, VERIFIED);
    verify(holdService, never()).freeze(any(), any());
  }

  @Test
  void process_personRequest_verifiesButHoldsPayoutWhenPartyIsHighRisk() {
    var userId = 1L;
    var requestId = UUID.randomUUID();
    var request = personRequest(requestId, userId, "38812121215");
    var user = sampleUser().id(userId).build();
    var countries = Countries.of("EE");

    given(userService.findByPersonalCode("38812121215")).willReturn(Optional.of(user));
    given(kycCountryService.getCountries(userId)).willReturn(Optional.of(countries));
    given(sanctionAndPepScreener.screeningOutcome(user, countries)).willReturn(CLEAR);
    given(riskLevels.isHighRisk(user.getPersonalCode())).willReturn(true);

    service.process(request);

    verify(holdService).holdPayout(requestId, HIGH_RISK, SYSTEM);
    verify(redemptionStatusService).changeStatus(requestId, VERIFIED);
  }

  @Test
  void process_personRequest_joinsSeveralHoldReasons() {
    var userId = 1L;
    var requestId = UUID.randomUUID();
    var request = personRequest(requestId, userId, "38812121215");
    var user = sampleUser().id(userId).build();
    var countries = Countries.of("EE");

    given(userService.findByPersonalCode("38812121215")).willReturn(Optional.of(user));
    given(kycCountryService.getCountries(userId)).willReturn(Optional.of(countries));
    given(sanctionAndPepScreener.screeningOutcome(user, countries)).willReturn(PEP_HIT);
    given(riskLevels.isHighRisk(user.getPersonalCode())).willReturn(true);

    service.process(request);

    verify(holdService).holdPayout(requestId, "PEP,HIGH_RISK", SYSTEM);
    verify(redemptionStatusService).changeStatus(requestId, VERIFIED);
  }

  @Test
  void process_personRequest_leavesRequestReservedWhenScreeningIsUnavailable() {
    var userId = 1L;
    var requestId = UUID.randomUUID();
    var request = personRequest(requestId, userId, "38812121215");
    var user = sampleUser().id(userId).build();
    var countries = Countries.of("EE");

    given(userService.findByPersonalCode("38812121215")).willReturn(Optional.of(user));
    given(kycCountryService.getCountries(userId)).willReturn(Optional.of(countries));
    given(sanctionAndPepScreener.screeningOutcome(user, countries)).willReturn(UNAVAILABLE);

    service.process(request);

    verifyNoInteractions(holdService);
    verify(redemptionStatusService, never()).changeStatus(any(), any());
    verify(riskLevels, never()).isHighRisk(any());
  }

  @Test
  void process_personRequest_screensThePartyNotTheActor() {
    var actorUserId = 1L;
    var childCode = "61506150006";
    var requestId = UUID.randomUUID();
    var request = personRequest(requestId, actorUserId, childCode);
    var child = sampleUser().id(2L).personalCode(childCode).build();
    var countries = Countries.of("EE");

    given(userService.findByPersonalCode(childCode)).willReturn(Optional.of(child));
    given(kycCountryService.getCountries(child.getId())).willReturn(Optional.of(countries));
    given(sanctionAndPepScreener.screeningOutcome(child, countries)).willReturn(CLEAR);
    given(riskLevels.isHighRisk(childCode)).willReturn(false);

    service.process(request);

    verify(redemptionStatusService).changeStatus(requestId, VERIFIED);
  }

  @Test
  void process_personRequest_screensAgainstCitizenshipsTheSurveyDoesNotCarry() {
    var userId = 1L;
    var requestId = UUID.randomUUID();
    var request = personRequest(requestId, userId, "38812121215");
    var user = sampleUser().id(userId).build();

    given(userService.findByPersonalCode("38812121215")).willReturn(Optional.of(user));
    given(kycCountryService.getCountries(userId)).willReturn(Optional.of(Countries.of("EE")));
    given(sanctionAndPepScreener.recordedCitizenships(user)).willReturn(Countries.of("RU"));
    given(sanctionAndPepScreener.screeningOutcome(user, Countries.of("EE", "RU")))
        .willReturn(CLEAR);
    given(riskLevels.isHighRisk(user.getPersonalCode())).willReturn(false);

    service.process(request);

    verify(sanctionAndPepScreener).screeningOutcome(user, Countries.of("EE", "RU"));
    verify(redemptionStatusService).changeStatus(requestId, VERIFIED);
  }

  @Test
  void process_personRequest_throwsWhenKycCountryMissing() {
    var userId = 1L;
    var request = personRequest(UUID.randomUUID(), userId, "38812121215");
    var user = sampleUser().id(userId).build();

    given(userService.findByPersonalCode("38812121215")).willReturn(Optional.of(user));
    given(kycCountryService.getCountries(userId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.process(request)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void process_personRequest_throwsWhenPartyUserNotFound() {
    var request = personRequest(UUID.randomUUID(), 1L, "61506150006");

    given(userService.findByPersonalCode("61506150006")).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.process(request)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void process_legalEntityRequest_verifiesWhenLatestKybCompleted() {
    var registryCode = "16001234";
    var requestId = UUID.randomUUID();
    var request = legalEntityRequest(requestId, registryCode);

    given(savingsFundOnboardingRepository.isOnboardingCompleted(registryCode, LEGAL_ENTITY))
        .willReturn(true);

    service.process(request);

    verify(redemptionStatusService).changeStatus(requestId, VERIFIED);
    verifyNoInteractions(holdService);
    verify(legalEntityScreener, never()).screenLatest(registryCode);
  }

  @Test
  void process_legalEntityRequest_holdsPayoutWhenLatestKybRejected() {
    var registryCode = "16001234";
    var requestId = UUID.randomUUID();
    var request = legalEntityRequest(requestId, registryCode);

    given(savingsFundOnboardingRepository.isOnboardingCompleted(registryCode, LEGAL_ENTITY))
        .willReturn(false);
    given(savingsFundOnboardingRepository.findStatus(registryCode, LEGAL_ENTITY))
        .willReturn(Optional.of(SavingsFundOnboardingStatus.REJECTED));

    service.process(request);

    verify(holdService).holdPayout(requestId, KYB_NOT_COMPLETED, SYSTEM);
    verify(redemptionStatusService).changeStatus(requestId, VERIFIED);
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
    given(legalEntityScreener.screenLatest(registryCode))
        .willReturn(List.of(new KybCheck(COMPANY_SANCTION, true, Map.of())));

    service.process(request);

    verify(redemptionStatusService).changeStatus(requestId, VERIFIED);
    verifyNoInteractions(holdService);
  }

  @Test
  void process_legalEntityRequest_reScreensWhenStatusPendingThenHoldsPayoutIfStillNotCompleted() {
    var registryCode = "16001234";
    var requestId = UUID.randomUUID();
    var request = legalEntityRequest(requestId, registryCode);

    given(savingsFundOnboardingRepository.isOnboardingCompleted(registryCode, LEGAL_ENTITY))
        .willReturn(false, false);
    given(savingsFundOnboardingRepository.findStatus(registryCode, LEGAL_ENTITY))
        .willReturn(Optional.of(SavingsFundOnboardingStatus.PENDING));
    given(legalEntityScreener.screenLatest(registryCode))
        .willReturn(List.of(new KybCheck(COMPANY_ACTIVE, false, Map.of())));

    service.process(request);

    verify(holdService).holdPayout(requestId, KYB_NOT_COMPLETED, SYSTEM);
    verify(redemptionStatusService).changeStatus(requestId, VERIFIED);
  }

  @Test
  void process_legalEntityRequest_freezesWhenReScreeningFindsACompanySanction() {
    var registryCode = "16001234";
    var requestId = UUID.randomUUID();
    var request = legalEntityRequest(requestId, registryCode);

    given(savingsFundOnboardingRepository.isOnboardingCompleted(registryCode, LEGAL_ENTITY))
        .willReturn(false);
    given(savingsFundOnboardingRepository.findStatus(registryCode, LEGAL_ENTITY))
        .willReturn(Optional.empty());
    given(legalEntityScreener.screenLatest(registryCode))
        .willReturn(
            List.of(
                new KybCheck(COMPANY_ACTIVE, true, Map.of()),
                new KybCheck(COMPANY_SANCTION, false, Map.of())));

    service.process(request);

    verify(holdService).freeze(requestId, SANCTION);
    verify(redemptionStatusService, never()).changeStatus(any(), any());
  }

  @Test
  void process_legalEntityRequest_holdsPayoutWhenScreenLatestThrows() {
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

    verify(holdService).holdPayout(requestId, KYB_SCREENING_FAILED, SYSTEM);
    verify(redemptionStatusService).changeStatus(requestId, VERIFIED);
  }

  private static RedemptionRequest personRequest(UUID requestId, long userId, String personalCode) {
    return redemptionRequestFixture()
        .id(requestId)
        .userId(userId)
        .partyType(PERSON)
        .partyCode(personalCode)
        .build();
  }

  private static RedemptionRequest legalEntityRequest(UUID requestId, String registryCode) {
    return redemptionRequestFixture()
        .id(requestId)
        .partyType(LEGAL_ENTITY)
        .partyCode(registryCode)
        .build();
  }
}
