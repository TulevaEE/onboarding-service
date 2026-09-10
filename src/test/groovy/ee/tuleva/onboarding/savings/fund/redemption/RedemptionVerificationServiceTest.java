package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.aml.ScreeningOutcome.CLEAR;
import static ee.tuleva.onboarding.aml.ScreeningOutcome.MATCH;
import static ee.tuleva.onboarding.aml.ScreeningOutcome.UNAVAILABLE;
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.AML;
import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionHoldReason.HIGH_RISK;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionHoldReason.ONBOARDING_INCOMPLETE;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionHoldReason.SCREENING_MATCH;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionHoldReason.SCREENING_UNAVAILABLE;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.VERIFIED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequestFixture.redemptionRequestFixture;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.aml.RiskLevels;
import ee.tuleva.onboarding.aml.SanctionAndPepScreener;
import ee.tuleva.onboarding.country.Countries;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.kyb.LegalEntityScreener;
import ee.tuleva.onboarding.kyc.KycCountryService;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.savings.SavingFundDeadlinesService;
import ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingRepository;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RedemptionVerificationServiceTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final String PERSONAL_CODE = "38812121215";
  private static final Instant THURSDAY_EVENING = Instant.parse("2026-08-27T19:19:35Z");
  private static final Instant FRIDAY_NOON = Instant.parse("2026-08-28T09:00:00Z");
  private static final Instant FRIDAY_HALF_PAST_THREE = Instant.parse("2026-08-28T12:30:00Z");

  @Mock private RedemptionStatusService redemptionStatusService;
  @Mock private UserService userService;
  @Mock private KycCountryService kycCountryService;
  @Mock private SanctionAndPepScreener sanctionAndPepScreener;
  @Mock private RiskLevels riskLevels;
  @Mock private SavingsFundOnboardingRepository savingsFundOnboardingRepository;
  @Mock private LegalEntityScreener legalEntityScreener;
  @Mock private OperationsNotificationService notificationService;

  private RedemptionVerificationService serviceAt(Instant now) {
    var clock = Clock.fixed(now, TALLINN);
    return new RedemptionVerificationService(
        redemptionStatusService,
        userService,
        kycCountryService,
        sanctionAndPepScreener,
        riskLevels,
        savingsFundOnboardingRepository,
        legalEntityScreener,
        notificationService,
        new SavingFundDeadlinesService(new PublicHolidays(), clock),
        clock);
  }

  private RedemptionVerificationService service() {
    return serviceAt(FRIDAY_NOON);
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

  private User givenPersonWithCountries() {
    var user = sampleUser().id(1L).personalCode(PERSONAL_CODE).build();
    given(userService.findByPersonalCode(PERSONAL_CODE)).willReturn(Optional.of(user));
    given(kycCountryService.getCountries(1L)).willReturn(Optional.of(Countries.of("EE")));
    return user;
  }

  @Test
  void process_personRequest_transitionsToVerifiedWhenScreeningClearAndNotHighRisk() {
    var requestId = UUID.randomUUID();
    var user = givenPersonWithCountries();
    given(sanctionAndPepScreener.screeningOutcome(user, Countries.of("EE"))).willReturn(CLEAR);
    given(riskLevels.isHighRisk(PERSONAL_CODE)).willReturn(false);

    service().process(personRequest(requestId));

    verify(redemptionStatusService).changeStatus(requestId, VERIFIED);
    verify(redemptionStatusService, never()).holdForReview(any(), any());
    verifyNoInteractions(notificationService);
  }

  @Test
  void process_personRequest_holdsForReviewWhenScreeningMatches() {
    var requestId = UUID.randomUUID();
    var user = givenPersonWithCountries();
    given(sanctionAndPepScreener.screeningOutcome(user, Countries.of("EE"))).willReturn(MATCH);

    service().process(personRequest(requestId));

    verify(redemptionStatusService).holdForReview(requestId, SCREENING_MATCH);
    verify(redemptionStatusService, never()).changeStatus(requestId, VERIFIED);
    verify(notificationService)
        .sendMessage(
            "AML: redemption held for review: id="
                + requestId
                + ", amount=10.00 EUR, reason=SCREENING_MATCH",
            AML);
  }

  @Test
  void process_personRequest_holdsRedemptionEvenWhenNotificationFails() {
    var requestId = UUID.randomUUID();
    var user = givenPersonWithCountries();
    given(sanctionAndPepScreener.screeningOutcome(user, Countries.of("EE"))).willReturn(MATCH);
    willThrow(new IllegalStateException("Slack unavailable"))
        .given(notificationService)
        .sendMessage(anyString(), any());

    service().process(personRequest(requestId));

    verify(redemptionStatusService).holdForReview(requestId, SCREENING_MATCH);
    verify(redemptionStatusService, never()).changeStatus(requestId, VERIFIED);
  }

  @Test
  void process_personRequest_holdsForReviewWhenPartyIsHighRisk() {
    var requestId = UUID.randomUUID();
    var user = givenPersonWithCountries();
    given(sanctionAndPepScreener.screeningOutcome(user, Countries.of("EE"))).willReturn(CLEAR);
    given(riskLevels.isHighRisk(PERSONAL_CODE)).willReturn(true);

    service().process(personRequest(requestId));

    verify(redemptionStatusService).holdForReview(requestId, HIGH_RISK);
    verify(redemptionStatusService, never()).changeStatus(requestId, VERIFIED);
  }

  @Test
  void process_personRequest_aMatchOutranksHighRiskAsTheHoldReason() {
    var requestId = UUID.randomUUID();
    var user = givenPersonWithCountries();
    given(sanctionAndPepScreener.screeningOutcome(user, Countries.of("EE"))).willReturn(MATCH);
    given(riskLevels.isHighRisk(PERSONAL_CODE)).willReturn(true);

    service().process(personRequest(requestId));

    verify(redemptionStatusService).holdForReview(requestId, SCREENING_MATCH);
  }

  @Test
  void
      process_personRequest_leavesTheRequestReservedWhileScreeningIsUnavailableBeforeTheDeadline() {
    var requestId = UUID.randomUUID();
    var user = givenPersonWithCountries();
    given(sanctionAndPepScreener.screeningOutcome(user, Countries.of("EE")))
        .willReturn(UNAVAILABLE);
    given(riskLevels.isHighRisk(PERSONAL_CODE)).willReturn(false);

    serviceAt(FRIDAY_NOON).process(personRequest(requestId));

    verifyNoInteractions(redemptionStatusService, notificationService);
  }

  @Test
  void process_personRequest_holdsAsScreeningUnavailableOnceTheRetryDeadlineHasPassed() {
    var requestId = UUID.randomUUID();
    var user = givenPersonWithCountries();
    given(sanctionAndPepScreener.screeningOutcome(user, Countries.of("EE")))
        .willReturn(UNAVAILABLE);
    given(riskLevels.isHighRisk(PERSONAL_CODE)).willReturn(false);

    serviceAt(FRIDAY_HALF_PAST_THREE).process(personRequest(requestId));

    verify(redemptionStatusService).holdForReview(requestId, SCREENING_UNAVAILABLE);
    verify(notificationService)
        .sendMessage(
            "AML: redemption held for review: id="
                + requestId
                + ", amount=10.00 EUR, reason=SCREENING_UNAVAILABLE",
            AML);
  }

  @Test
  void process_personRequest_holdsAHighRiskPartyAtOnceEvenWhenScreeningIsUnavailable() {
    var requestId = UUID.randomUUID();
    var user = givenPersonWithCountries();
    given(sanctionAndPepScreener.screeningOutcome(user, Countries.of("EE")))
        .willReturn(UNAVAILABLE);
    given(riskLevels.isHighRisk(PERSONAL_CODE)).willReturn(true);

    serviceAt(FRIDAY_NOON).process(personRequest(requestId));

    verify(redemptionStatusService).holdForReview(requestId, HIGH_RISK);
  }

  @Test
  void process_personRequest_screensThePartyNotTheActor() {
    var childCode = "61506150006";
    var requestId = UUID.randomUUID();
    var request =
        redemptionRequestFixture()
            .id(requestId)
            .userId(1L)
            .partyType(PERSON)
            .partyCode(childCode)
            .requestedAt(THURSDAY_EVENING)
            .build();
    var child = sampleUser().id(2L).personalCode(childCode).build();
    given(userService.findByPersonalCode(childCode)).willReturn(Optional.of(child));
    given(kycCountryService.getCountries(2L)).willReturn(Optional.of(Countries.of("EE")));
    given(sanctionAndPepScreener.screeningOutcome(child, Countries.of("EE"))).willReturn(CLEAR);
    given(riskLevels.isHighRisk(childCode)).willReturn(false);

    service().process(request);

    verify(redemptionStatusService).changeStatus(requestId, VERIFIED);
  }

  @Test
  void process_personRequest_throwsWhenKycCountryMissing() {
    var user = sampleUser().id(1L).personalCode(PERSONAL_CODE).build();
    given(userService.findByPersonalCode(PERSONAL_CODE)).willReturn(Optional.of(user));
    given(kycCountryService.getCountries(1L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service().process(personRequest(UUID.randomUUID())))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void process_personRequest_throwsWhenPartyUserNotFound() {
    given(userService.findByPersonalCode(PERSONAL_CODE)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service().process(personRequest(UUID.randomUUID())))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void process_legalEntityRequest_transitionsToVerifiedWhenLatestKybCompleted() {
    var registryCode = "16001234";
    var requestId = UUID.randomUUID();
    given(savingsFundOnboardingRepository.isOnboardingCompleted(registryCode, LEGAL_ENTITY))
        .willReturn(true);

    service().process(legalEntityRequest(requestId, registryCode));

    verify(redemptionStatusService).changeStatus(requestId, VERIFIED);
    verify(redemptionStatusService, never()).holdForReview(any(), any());
    verify(legalEntityScreener, never()).screenLatest(registryCode);
  }

  @Test
  void process_legalEntityRequest_holdsForReviewWhenLatestKybRejected() {
    var registryCode = "16001234";
    var requestId = UUID.randomUUID();
    given(savingsFundOnboardingRepository.isOnboardingCompleted(registryCode, LEGAL_ENTITY))
        .willReturn(false);
    given(savingsFundOnboardingRepository.findStatus(registryCode, LEGAL_ENTITY))
        .willReturn(Optional.of(SavingsFundOnboardingStatus.REJECTED));

    service().process(legalEntityRequest(requestId, registryCode));

    verify(redemptionStatusService).holdForReview(requestId, ONBOARDING_INCOMPLETE);
    verify(redemptionStatusService, never()).changeStatus(requestId, VERIFIED);
    verify(legalEntityScreener, never()).screenLatest(registryCode);
  }

  @Test
  void process_legalEntityRequest_reScreensWhenStatusMissingThenVerifiesIfCompleted() {
    var registryCode = "16001234";
    var requestId = UUID.randomUUID();
    given(savingsFundOnboardingRepository.isOnboardingCompleted(registryCode, LEGAL_ENTITY))
        .willReturn(false, true);
    given(savingsFundOnboardingRepository.findStatus(registryCode, LEGAL_ENTITY))
        .willReturn(Optional.empty());

    service().process(legalEntityRequest(requestId, registryCode));

    verify(legalEntityScreener).screenLatest(registryCode);
    verify(redemptionStatusService).changeStatus(requestId, VERIFIED);
  }

  @Test
  void process_legalEntityRequest_reScreensWhenStatusPendingThenHoldsIfStillIncomplete() {
    var registryCode = "16001234";
    var requestId = UUID.randomUUID();
    given(savingsFundOnboardingRepository.isOnboardingCompleted(registryCode, LEGAL_ENTITY))
        .willReturn(false, false);
    given(savingsFundOnboardingRepository.findStatus(registryCode, LEGAL_ENTITY))
        .willReturn(Optional.of(SavingsFundOnboardingStatus.PENDING));

    service().process(legalEntityRequest(requestId, registryCode));

    verify(legalEntityScreener).screenLatest(registryCode);
    verify(redemptionStatusService).holdForReview(requestId, ONBOARDING_INCOMPLETE);
  }

  @Test
  void
      process_legalEntityRequest_leavesTheRequestReservedWhileTheScreenerIsUnavailableBeforeTheDeadline() {
    var registryCode = "16001234";
    var requestId = UUID.randomUUID();
    given(savingsFundOnboardingRepository.isOnboardingCompleted(registryCode, LEGAL_ENTITY))
        .willReturn(false);
    given(savingsFundOnboardingRepository.findStatus(registryCode, LEGAL_ENTITY))
        .willReturn(Optional.empty());
    willThrow(new IllegalStateException("Ariregister unavailable"))
        .given(legalEntityScreener)
        .screenLatest(registryCode);

    serviceAt(FRIDAY_NOON).process(legalEntityRequest(requestId, registryCode));

    verifyNoInteractions(redemptionStatusService, notificationService);
  }

  @Test
  void process_legalEntityRequest_holdsAsScreeningUnavailableOnceTheRetryDeadlineHasPassed() {
    var registryCode = "16001234";
    var requestId = UUID.randomUUID();
    given(savingsFundOnboardingRepository.isOnboardingCompleted(registryCode, LEGAL_ENTITY))
        .willReturn(false);
    given(savingsFundOnboardingRepository.findStatus(registryCode, LEGAL_ENTITY))
        .willReturn(Optional.empty());
    willThrow(new IllegalStateException("Ariregister unavailable"))
        .given(legalEntityScreener)
        .screenLatest(registryCode);

    serviceAt(FRIDAY_HALF_PAST_THREE).process(legalEntityRequest(requestId, registryCode));

    verify(redemptionStatusService).holdForReview(requestId, SCREENING_UNAVAILABLE);
    verify(redemptionStatusService, never()).changeStatus(requestId, VERIFIED);
  }

  private static RedemptionRequest legalEntityRequest(UUID requestId, String registryCode) {
    return redemptionRequestFixture()
        .id(requestId)
        .partyType(LEGAL_ENTITY)
        .partyCode(registryCode)
        .requestedAt(THURSDAY_EVENING)
        .build();
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
    verify(redemptionStatusService).changeStatus(requestId, VERIFIED);
  }
}
