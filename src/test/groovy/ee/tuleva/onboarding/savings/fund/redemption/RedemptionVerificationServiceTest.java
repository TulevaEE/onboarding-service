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
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionHoldReason.HIGH_RISK;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionHoldReason.KYB_SCREENING_FAILED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionHoldReason.ONBOARDING_INCOMPLETE;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionHoldReason.PEP;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionHoldReason.SCREENING_UNAVAILABLE;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.RESERVED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.VERIFIED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequestFixture.redemptionRequestFixture;
import static org.assertj.core.api.Assertions.assertThat;
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
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.LegalEntityScreener;
import ee.tuleva.onboarding.kyc.KycCountryService;
import ee.tuleva.onboarding.savings.SavingFundDeadlinesService;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingRepository;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ws.client.WebServiceIOException;

@ExtendWith(MockitoExtension.class)
class RedemptionVerificationServiceTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final String PERSONAL_CODE = "38812121215";
  private static final String REGISTRY_CODE = "16001234";
  private static final Instant THURSDAY_EVENING = Instant.parse("2026-08-27T19:19:35Z");
  private static final Instant FRIDAY_NOON = Instant.parse("2026-08-28T09:00:00Z");
  private static final Instant FRIDAY_HALF_PAST_THREE = Instant.parse("2026-08-28T12:30:00Z");

  @Mock private RedemptionRequestRepository redemptionRequestRepository;
  @Mock private RedemptionStatusService redemptionStatusService;
  @Mock private RedemptionHoldService holdService;
  @Mock private UserService userService;
  @Mock private KycCountryService kycCountryService;
  @Mock private SanctionAndPepScreener sanctionAndPepScreener;
  @Mock private RiskLevels riskLevels;
  @Mock private SavingsFundOnboardingRepository savingsFundOnboardingRepository;
  @Mock private LegalEntityScreener legalEntityScreener;

  private RedemptionVerificationService serviceAt(Instant now) {
    var clock = Clock.fixed(now, TALLINN);
    return new RedemptionVerificationService(
        redemptionRequestRepository,
        redemptionStatusService,
        holdService,
        userService,
        kycCountryService,
        sanctionAndPepScreener,
        riskLevels,
        savingsFundOnboardingRepository,
        legalEntityScreener,
        new SavingFundDeadlinesService(new PublicHolidays(), clock),
        clock);
  }

  private RedemptionVerificationService service() {
    return serviceAt(FRIDAY_NOON);
  }

  @Test
  void process_personRequest_verifiesWhenScreeningClearAndNotHighRisk() {
    var requestId = UUID.randomUUID();
    var user = givenPersonWithCountries();
    given(sanctionAndPepScreener.screeningOutcome(user, Countries.of("EE"))).willReturn(CLEAR);
    given(riskLevels.isHighRisk(PERSONAL_CODE)).willReturn(false);

    service().process(personRequest(requestId));

    verify(redemptionStatusService).changeStatus(requestId, RESERVED, VERIFIED);
    verifyNoInteractions(holdService);
  }

  @Test
  void process_personRequest_freezesTheOrderOnASanctionsHit() {
    var requestId = UUID.randomUUID();
    var user = givenPersonWithCountries();
    given(sanctionAndPepScreener.screeningOutcome(user, Countries.of("EE")))
        .willReturn(SANCTION_HIT);

    service().process(personRequest(requestId));

    verify(holdService).freeze(requestId);
    verify(holdService, never()).holdPayout(any(), any());
    verifyNoInteractions(redemptionStatusService, riskLevels);
  }

  @Test
  void process_personRequest_executesTheOrderButHoldsThePayoutOnAPepHit() {
    var requestId = UUID.randomUUID();
    var user = givenPersonWithCountries();
    given(sanctionAndPepScreener.screeningOutcome(user, Countries.of("EE"))).willReturn(PEP_HIT);
    given(riskLevels.isHighRisk(PERSONAL_CODE)).willReturn(false);

    service().process(personRequest(requestId));

    verify(holdService).holdPayout(requestId, Set.of(PEP));
    verify(redemptionStatusService).changeStatus(requestId, RESERVED, VERIFIED);
    verify(holdService, never()).freeze(any());
  }

  @Test
  void process_personRequest_executesTheOrderButHoldsThePayoutWhenThePartyIsHighRisk() {
    var requestId = UUID.randomUUID();
    var user = givenPersonWithCountries();
    given(sanctionAndPepScreener.screeningOutcome(user, Countries.of("EE"))).willReturn(CLEAR);
    given(riskLevels.isHighRisk(PERSONAL_CODE)).willReturn(true);

    service().process(personRequest(requestId));

    verify(holdService).holdPayout(requestId, Set.of(HIGH_RISK));
    verify(redemptionStatusService).changeStatus(requestId, RESERVED, VERIFIED);
  }

  @Test
  void process_personRequest_recordsEveryHoldReasonThatApplies() {
    var requestId = UUID.randomUUID();
    var user = givenPersonWithCountries();
    given(sanctionAndPepScreener.screeningOutcome(user, Countries.of("EE"))).willReturn(PEP_HIT);
    given(riskLevels.isHighRisk(PERSONAL_CODE)).willReturn(true);

    service().process(personRequest(requestId));

    verify(holdService).holdPayout(requestId, Set.of(PEP, HIGH_RISK));
    verify(redemptionStatusService).changeStatus(requestId, RESERVED, VERIFIED);
  }

  @Test
  void process_personRequest_recordsTheAttemptAndRetriesWhileScreeningIsUnavailable() {
    var request = personRequest(UUID.randomUUID());
    var user = givenPersonWithCountries();
    given(sanctionAndPepScreener.screeningOutcome(user, Countries.of("EE")))
        .willReturn(UNAVAILABLE);

    serviceAt(FRIDAY_NOON).process(request);

    assertThat(request.getVerificationAttemptedAt()).isEqualTo(FRIDAY_NOON);
    verify(redemptionRequestRepository).save(request);
    verifyNoInteractions(holdService, redemptionStatusService, riskLevels);
  }

  @Test
  void process_personRequest_executesTheOrderAndHoldsThePayoutOnceTheRetryDeadlineHasPassed() {
    var requestId = UUID.randomUUID();
    var user = givenPersonWithCountries();
    given(sanctionAndPepScreener.screeningOutcome(user, Countries.of("EE")))
        .willReturn(UNAVAILABLE);

    serviceAt(FRIDAY_HALF_PAST_THREE).process(personRequest(requestId));

    verify(holdService).holdPayout(requestId, Set.of(SCREENING_UNAVAILABLE));
    verify(redemptionStatusService).changeStatus(requestId, RESERVED, VERIFIED);
  }

  @Test
  void process_personRequest_skipsAScreeningAttemptMadeWithinTheLastFiveMinutes() {
    var request = personRequest(UUID.randomUUID());
    request.setVerificationAttemptedAt(FRIDAY_NOON.minusSeconds(120));

    serviceAt(FRIDAY_NOON).process(request);

    verifyNoInteractions(userService, sanctionAndPepScreener, redemptionStatusService, holdService);
  }

  @Test
  void process_personRequest_retriesOnceTheBackoffHasElapsed() {
    var requestId = UUID.randomUUID();
    var user = givenPersonWithCountries();
    given(sanctionAndPepScreener.screeningOutcome(user, Countries.of("EE"))).willReturn(CLEAR);
    given(riskLevels.isHighRisk(PERSONAL_CODE)).willReturn(false);
    var request = personRequest(requestId);
    request.setVerificationAttemptedAt(FRIDAY_NOON.minusSeconds(6 * 60));

    serviceAt(FRIDAY_NOON).process(request);

    verify(redemptionStatusService).changeStatus(requestId, RESERVED, VERIFIED);
  }

  @Test
  void process_personRequest_screensThePartyNotTheActor() {
    var childCode = "61506150006";
    var requestId = UUID.randomUUID();
    var child = sampleUser().id(2L).personalCode(childCode).build();
    given(userService.findByPersonalCode(childCode)).willReturn(Optional.of(child));
    given(kycCountryService.getCountries(2L)).willReturn(Optional.of(Countries.of("EE")));
    given(sanctionAndPepScreener.screeningOutcome(child, Countries.of("EE"))).willReturn(CLEAR);
    given(riskLevels.isHighRisk(childCode)).willReturn(false);

    service()
        .process(
            redemptionRequestFixture()
                .id(requestId)
                .userId(1L)
                .partyType(PERSON)
                .partyCode(childCode)
                .requestedAt(THURSDAY_EVENING)
                .build());

    verify(redemptionStatusService).changeStatus(requestId, RESERVED, VERIFIED);
  }

  @Test
  void process_personRequest_screensAgainstCitizenshipsTheSurveyDoesNotCarry() {
    var requestId = UUID.randomUUID();
    var user = givenPersonWithCountries();
    given(sanctionAndPepScreener.recordedCitizenships(user)).willReturn(Countries.of("RU"));
    given(sanctionAndPepScreener.screeningOutcome(user, Countries.of("EE", "RU")))
        .willReturn(CLEAR);
    given(riskLevels.isHighRisk(PERSONAL_CODE)).willReturn(false);

    service().process(personRequest(requestId));

    verify(sanctionAndPepScreener).screeningOutcome(user, Countries.of("EE", "RU"));
    verify(redemptionStatusService).changeStatus(requestId, RESERVED, VERIFIED);
  }

  @Test
  void process_personRequest_throwsWhenKycCountryMissing() {
    var user = sampleUser().id(1L).personalCode(PERSONAL_CODE).build();
    given(userService.findByPersonalCode(PERSONAL_CODE)).willReturn(Optional.of(user));
    given(kycCountryService.getCountries(1L)).willReturn(Optional.empty());
    var service = service();
    var request = personRequest(UUID.randomUUID());

    assertThatThrownBy(() -> service.process(request)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void process_personRequest_throwsWhenPartyUserNotFound() {
    given(userService.findByPersonalCode(PERSONAL_CODE)).willReturn(Optional.empty());
    var service = service();
    var request = personRequest(UUID.randomUUID());

    assertThatThrownBy(() -> service.process(request)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void process_legalEntityRequest_verifiesWhenLatestKybCompleted() {
    var requestId = UUID.randomUUID();
    given(savingsFundOnboardingRepository.isOnboardingCompleted(REGISTRY_CODE, LEGAL_ENTITY))
        .willReturn(true);

    service().process(legalEntityRequest(requestId));

    verify(redemptionStatusService).changeStatus(requestId, RESERVED, VERIFIED);
    verifyNoInteractions(holdService, legalEntityScreener);
  }

  @Test
  void process_legalEntityRequest_reScreensARejectedCompanyAndFreezesOnItsSanction() {
    var requestId = UUID.randomUUID();
    given(savingsFundOnboardingRepository.isOnboardingCompleted(REGISTRY_CODE, LEGAL_ENTITY))
        .willReturn(false);
    given(legalEntityScreener.screenLatest(REGISTRY_CODE))
        .willReturn(List.of(new KybCheck(COMPANY_SANCTION, false, Map.of())));

    service().process(legalEntityRequest(requestId));

    verify(holdService).freeze(requestId);
    verifyNoInteractions(redemptionStatusService);
  }

  @Test
  void process_legalEntityRequest_holdsThePayoutWhenARejectedCompanyHasNoSanction() {
    var requestId = UUID.randomUUID();
    given(savingsFundOnboardingRepository.isOnboardingCompleted(REGISTRY_CODE, LEGAL_ENTITY))
        .willReturn(false, false);
    given(legalEntityScreener.screenLatest(REGISTRY_CODE))
        .willReturn(List.of(new KybCheck(COMPANY_ACTIVE, false, Map.of())));

    service().process(legalEntityRequest(requestId));

    verify(holdService).holdPayout(requestId, Set.of(ONBOARDING_INCOMPLETE));
    verify(redemptionStatusService).changeStatus(requestId, RESERVED, VERIFIED);
  }

  @Test
  void process_legalEntityRequest_reScreensThenVerifiesIfNowCompleted() {
    var requestId = UUID.randomUUID();
    given(savingsFundOnboardingRepository.isOnboardingCompleted(REGISTRY_CODE, LEGAL_ENTITY))
        .willReturn(false, true);
    given(legalEntityScreener.screenLatest(REGISTRY_CODE))
        .willReturn(List.of(new KybCheck(COMPANY_SANCTION, true, Map.of())));

    service().process(legalEntityRequest(requestId));

    verify(redemptionStatusService).changeStatus(requestId, RESERVED, VERIFIED);
    verifyNoInteractions(holdService);
  }

  @Test
  void process_legalEntityRequest_reScreensThenHoldsThePayoutIfStillIncomplete() {
    var requestId = UUID.randomUUID();
    given(savingsFundOnboardingRepository.isOnboardingCompleted(REGISTRY_CODE, LEGAL_ENTITY))
        .willReturn(false, false);
    given(legalEntityScreener.screenLatest(REGISTRY_CODE))
        .willReturn(List.of(new KybCheck(COMPANY_ACTIVE, false, Map.of())));

    service().process(legalEntityRequest(requestId));

    verify(holdService).holdPayout(requestId, Set.of(ONBOARDING_INCOMPLETE));
    verify(redemptionStatusService).changeStatus(requestId, RESERVED, VERIFIED);
  }

  @Test
  void process_legalEntityRequest_freezesTheOrderWhenReScreeningFindsACompanySanction() {
    var requestId = UUID.randomUUID();
    given(savingsFundOnboardingRepository.isOnboardingCompleted(REGISTRY_CODE, LEGAL_ENTITY))
        .willReturn(false);
    given(legalEntityScreener.screenLatest(REGISTRY_CODE))
        .willReturn(
            List.of(
                new KybCheck(COMPANY_ACTIVE, true, Map.of()),
                new KybCheck(COMPANY_SANCTION, false, Map.of())));

    service().process(legalEntityRequest(requestId));

    verify(holdService).freeze(requestId);
    verifyNoInteractions(redemptionStatusService);
  }

  @Test
  void process_legalEntityRequest_retriesWhileTheScreeningServiceIsUnreachable() {
    var request = legalEntityRequest(UUID.randomUUID());
    given(savingsFundOnboardingRepository.isOnboardingCompleted(REGISTRY_CODE, LEGAL_ENTITY))
        .willReturn(false);
    willThrow(new WebServiceIOException("Ariregister unreachable"))
        .given(legalEntityScreener)
        .screenLatest(REGISTRY_CODE);

    serviceAt(FRIDAY_NOON).process(request);

    assertThat(request.getVerificationAttemptedAt()).isEqualTo(FRIDAY_NOON);
    verifyNoInteractions(holdService, redemptionStatusService);
  }

  @Test
  void process_legalEntityRequest_holdsThePayoutOnceTheScreeningRetryDeadlineHasPassed() {
    var requestId = UUID.randomUUID();
    given(savingsFundOnboardingRepository.isOnboardingCompleted(REGISTRY_CODE, LEGAL_ENTITY))
        .willReturn(false);
    willThrow(new WebServiceIOException("Ariregister unreachable"))
        .given(legalEntityScreener)
        .screenLatest(REGISTRY_CODE);

    serviceAt(FRIDAY_HALF_PAST_THREE).process(legalEntityRequest(requestId));

    verify(holdService).holdPayout(requestId, Set.of(SCREENING_UNAVAILABLE));
    verify(redemptionStatusService).changeStatus(requestId, RESERVED, VERIFIED);
  }

  @Test
  void process_legalEntityRequest_holdsThePayoutAtOnceWhenTheScreenerFailsOnMissingData() {
    var requestId = UUID.randomUUID();
    given(savingsFundOnboardingRepository.isOnboardingCompleted(REGISTRY_CODE, LEGAL_ENTITY))
        .willReturn(false);
    willThrow(new IllegalStateException("No board members"))
        .given(legalEntityScreener)
        .screenLatest(REGISTRY_CODE);

    service().process(legalEntityRequest(requestId));

    verify(holdService).holdPayout(requestId, Set.of(KYB_SCREENING_FAILED));
    verify(redemptionStatusService).changeStatus(requestId, RESERVED, VERIFIED);
  }

  private User givenPersonWithCountries() {
    var user = sampleUser().id(1L).personalCode(PERSONAL_CODE).build();
    given(userService.findByPersonalCode(PERSONAL_CODE)).willReturn(Optional.of(user));
    given(kycCountryService.getCountries(1L)).willReturn(Optional.of(Countries.of("EE")));
    return user;
  }

  private static RedemptionRequest personRequest(UUID requestId) {
    return redemptionRequestFixture()
        .id(requestId)
        .userId(1L)
        .partyType(PERSON)
        .partyCode(PERSONAL_CODE)
        .requestedAt(THURSDAY_EVENING)
        .build();
  }

  private static RedemptionRequest legalEntityRequest(UUID requestId) {
    return redemptionRequestFixture()
        .id(requestId)
        .partyType(LEGAL_ENTITY)
        .partyCode(REGISTRY_CODE)
        .requestedAt(THURSDAY_EVENING)
        .build();
  }
}
