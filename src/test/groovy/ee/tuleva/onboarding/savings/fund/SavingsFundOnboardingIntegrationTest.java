package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.authenticatedPersonFromUser;
import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember;
import static ee.tuleva.onboarding.currency.Currency.EUR;
import static ee.tuleva.onboarding.event.TrackableEventType.SAVINGS_FUND_ONBOARDING_STATUS_CHANGE;
import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.*;
import static ee.tuleva.onboarding.kyc.KycSurveyPurpose.IDENTITY_ONLY;
import static ee.tuleva.onboarding.kyc.KycSurveyPurpose.PERSONAL_ONBOARDING;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.account.SavingsFundStatementService;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.event.TrackableEvent;
import ee.tuleva.onboarding.kyc.KycCheck;
import ee.tuleva.onboarding.kyc.KycCheckPerformedEvent;
import ee.tuleva.onboarding.ledger.LedgerParty;
import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.ledger.PartyRef;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.SavingFundPayment;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@RecordApplicationEvents
class SavingsFundOnboardingIntegrationTest {

  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private SavingsFundOnboardingRepository repository;
  @Autowired private UserRepository userRepository;
  @Autowired private ApplicationEvents applicationEvents;
  @Autowired private SavingsFundAccountClosureService closureService;
  @Autowired private SavingFundPaymentRepository paymentRepository;
  @Autowired private SavingsFundStatementService statementService;
  @Autowired private LedgerService ledgerService;
  @Autowired private SavingsFundLedger savingsFundLedger;

  private User user;

  @BeforeEach
  void setUp() {
    user = userRepository.save(sampleUserNonMember().personalCode("39802077017").id(null).build());
  }

  @Test
  void onKycCheckPerformed_mapsNoneRiskToCompleted() {
    var event =
        new KycCheckPerformedEvent(
            this, user.getPersonalCode(), new KycCheck(NONE, Map.of()), PERSONAL_ONBOARDING);

    eventPublisher.publishEvent(event);

    assertThat(repository.findStatus(user.getPersonalCode(), PERSON)).contains(COMPLETED);
  }

  @Test
  void onKycCheckPerformed_mapsLowRiskToCompleted() {
    var event =
        new KycCheckPerformedEvent(
            this, user.getPersonalCode(), new KycCheck(LOW, Map.of()), PERSONAL_ONBOARDING);

    eventPublisher.publishEvent(event);

    assertThat(repository.findStatus(user.getPersonalCode(), PERSON)).contains(COMPLETED);
  }

  @Test
  void onKycCheckPerformed_mapsMediumRiskToPending() {
    var event =
        new KycCheckPerformedEvent(
            this, user.getPersonalCode(), new KycCheck(MEDIUM, Map.of()), PERSONAL_ONBOARDING);

    eventPublisher.publishEvent(event);

    assertThat(repository.findStatus(user.getPersonalCode(), PERSON)).contains(PENDING);
  }

  @Test
  void onKycCheckPerformed_mapsHighRiskToRejected() {
    var event =
        new KycCheckPerformedEvent(
            this, user.getPersonalCode(), new KycCheck(HIGH, Map.of()), PERSONAL_ONBOARDING);

    eventPublisher.publishEvent(event);

    assertThat(repository.findStatus(user.getPersonalCode(), PERSON)).contains(REJECTED);
  }

  @Test
  void onKycCheckPerformed_doesNotUpdateIfAlreadyCompleted() {
    repository.saveOnboardingStatus(user.getPersonalCode(), PERSON, COMPLETED);
    var event =
        new KycCheckPerformedEvent(
            this, user.getPersonalCode(), new KycCheck(HIGH, Map.of()), PERSONAL_ONBOARDING);

    eventPublisher.publishEvent(event);

    assertThat(repository.findStatus(user.getPersonalCode(), PERSON)).contains(COMPLETED);
  }

  @Test
  void onKycCheckPerformed_createsRecordWhenNoneExists() {
    assertThat(repository.findStatus(user.getPersonalCode(), PERSON)).isEqualTo(Optional.empty());

    var event =
        new KycCheckPerformedEvent(
            this, user.getPersonalCode(), new KycCheck(LOW, Map.of()), PERSONAL_ONBOARDING);

    eventPublisher.publishEvent(event);

    assertThat(repository.findStatus(user.getPersonalCode(), PERSON)).contains(COMPLETED);
  }

  @Test
  void onKycCheckPerformed_identityOnly_createsNoRecord() {
    var event =
        new KycCheckPerformedEvent(
            this, user.getPersonalCode(), new KycCheck(LOW, Map.of()), IDENTITY_ONLY);

    eventPublisher.publishEvent(event);

    assertThat(repository.findStatus(user.getPersonalCode(), PERSON)).isEmpty();
    assertThat(applicationEvents.stream(TrackableEvent.class)).isEmpty();
  }

  @Test
  void onKycCheckPerformed_identityOnly_leavesExistingPendingUnchanged() {
    repository.saveOnboardingStatus(user.getPersonalCode(), PERSON, PENDING);
    var event =
        new KycCheckPerformedEvent(
            this, user.getPersonalCode(), new KycCheck(LOW, Map.of()), IDENTITY_ONLY);

    eventPublisher.publishEvent(event);

    assertThat(repository.findStatus(user.getPersonalCode(), PERSON)).contains(PENDING);
  }

  @Test
  void onKycCheckPerformed_identityOnly_leavesExistingRejectedUnchanged() {
    repository.saveOnboardingStatus(user.getPersonalCode(), PERSON, REJECTED);
    var event =
        new KycCheckPerformedEvent(
            this, user.getPersonalCode(), new KycCheck(LOW, Map.of()), IDENTITY_ONLY);

    eventPublisher.publishEvent(event);

    assertThat(repository.findStatus(user.getPersonalCode(), PERSON)).contains(REJECTED);
  }

  @Test
  void onKycCheckPerformed_identityOnly_leavesExistingCompletedUnchanged() {
    repository.saveOnboardingStatus(user.getPersonalCode(), PERSON, COMPLETED);
    var event =
        new KycCheckPerformedEvent(
            this, user.getPersonalCode(), new KycCheck(HIGH, Map.of()), IDENTITY_ONLY);

    eventPublisher.publishEvent(event);

    assertThat(repository.findStatus(user.getPersonalCode(), PERSON)).contains(COMPLETED);
  }

  @Test
  void onKycCheckPerformed_publishesTrackableEventOnStatusChange() {
    var event =
        new KycCheckPerformedEvent(
            this, user.getPersonalCode(), new KycCheck(LOW, Map.of()), PERSONAL_ONBOARDING);

    eventPublisher.publishEvent(event);

    var trackableEvents = applicationEvents.stream(TrackableEvent.class).toList();
    assertThat(trackableEvents).hasSize(1);

    var trackableEvent = trackableEvents.getFirst();
    assertThat(trackableEvent.getType()).isEqualTo(SAVINGS_FUND_ONBOARDING_STATUS_CHANGE);
    assertThat(trackableEvent.getPerson().getPersonalCode()).isEqualTo(user.getPersonalCode());
    assertThat(trackableEvent.getData()).isEqualTo(Map.of("newStatus", COMPLETED));
  }

  @Test
  void closeAccount_emptyAccountWithNothingPending_isClosedAndLooksNeverOpened() {
    var person = openEmptyAccount();
    assertThat(closureService.isClosable(person)).isTrue();

    closureService.close(person);

    assertThat(repository.findStatus(user.getPersonalCode(), PERSON)).contains(CLOSED);
    assertThat(closureService.isClosable(person)).isFalse();
    assertThat(statementService.getAccountStatement(person)).isEmpty();
  }

  @Test
  void closeAccount_closedAccountOpensAgainWhenThePersonOnboardsAgain() {
    closureService.close(openEmptyAccount());

    eventPublisher.publishEvent(
        new KycCheckPerformedEvent(
            this, user.getPersonalCode(), new KycCheck(LOW, Map.of()), PERSONAL_ONBOARDING));

    assertThat(repository.findStatus(user.getPersonalCode(), PERSON)).contains(COMPLETED);
  }

  @Test
  void closeAccount_accountHoldingMoney_isRefused() {
    var person = openEmptyAccount();
    savingsFundLedger.recordPaymentReceived(
        ledgerParty(), new BigDecimal("100.00"), UUID.randomUUID());

    assertThat(closureService.isClosable(person)).isFalse();
    assertThatThrownBy(() -> closureService.close(person))
        .isInstanceOf(SavingsFundAccountClosureRefusedException.class);
    assertThat(repository.findStatus(user.getPersonalCode(), PERSON)).contains(COMPLETED);
  }

  @Test
  void closeAccount_accountWithPaymentInProgress_isRefused() {
    var person = openEmptyAccount();
    var paymentId = paymentRepository.savePaymentData(payment());
    paymentRepository.attachParty(paymentId, new PartyId(PERSON, user.getPersonalCode()));
    paymentRepository.changeStatus(paymentId, SavingFundPayment.Status.RECEIVED);

    assertThat(closureService.isClosable(person)).isFalse();
    assertThatThrownBy(() -> closureService.close(person))
        .isInstanceOf(SavingsFundAccountClosureRefusedException.class);
  }

  private AuthenticatedPerson openEmptyAccount() {
    repository.saveOnboardingStatus(user.getPersonalCode(), PERSON, COMPLETED);
    ledgerService.initializeAccounts(ledgerParty());
    return authenticatedPersonFromUser(user).build();
  }

  private PartyRef ledgerParty() {
    return new PartyRef(LedgerParty.PartyType.PERSON, user.getPersonalCode());
  }

  private SavingFundPayment payment() {
    return SavingFundPayment.builder()
        .amount(new BigDecimal("100.00"))
        .currency(EUR)
        .description("Test payment")
        .remitterIban("EE471000001020145685")
        .remitterIdCode(user.getPersonalCode())
        .remitterName("Test Person")
        .beneficiaryIban("EE442200221092874625")
        .beneficiaryIdCode("14118923")
        .beneficiaryName("TULEVA FONDID AS")
        .externalId("2026101312345-1")
        .receivedBefore(Instant.parse("2026-10-13T09:00:00Z"))
        .build();
  }
}
