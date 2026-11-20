package ee.tuleva.onboarding.taxwin;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.authenticatedPersonFromUser;
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.taxwin.TaxDataConsentFixture.CONFIRM_REFERENCE;
import static ee.tuleva.onboarding.taxwin.TaxDataConsentFixture.CONSENT_PDF;
import static ee.tuleva.onboarding.taxwin.TaxDataConsentFixture.CONSENT_REFERENCE;
import static ee.tuleva.onboarding.taxwin.TaxDataConsentFixture.SIGNED_CONTAINER;
import static ee.tuleva.onboarding.taxwin.TaxDataConsentFixture.approved;
import static ee.tuleva.onboarding.taxwin.TaxDataConsentFixture.awaitingSignature;
import static ee.tuleva.onboarding.taxwin.TaxDataConsentFixture.signed;
import static ee.tuleva.onboarding.taxwin.TaxDataConsentService.leaseUntil;
import static ee.tuleva.onboarding.taxwin.TaxDataConsentStatus.APPROVED;
import static ee.tuleva.onboarding.taxwin.TaxDataConsentStatus.AWAITING_SIGNATURE;
import static ee.tuleva.onboarding.taxwin.TaxDataConsentStatus.INVALID;
import static ee.tuleva.onboarding.taxwin.TaxDataConsentStatus.SIGNED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.role.Role;
import ee.tuleva.onboarding.auth.role.RoleType;
import ee.tuleva.onboarding.error.NotFoundException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TaxDataConsentServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");
  private static final Long ID = 7L;

  @Mock private TaxDataConsentRepository repository;
  @Mock private ConsentServiceClient consentServiceClient;
  @Mock private TaxIncomeStore incomeStore;

  private TaxDataConsentService service;
  private final AuthenticatedPerson person =
      authenticatedPersonFromUser(sampleUser().build()).build();
  private final Long userId = person.getUserIdOrThrow();

  @BeforeEach
  void setUp() {
    service =
        new TaxDataConsentService(
            repository,
            consentServiceClient,
            incomeStore,
            Clock.fixed(NOW, ZoneId.of("Europe/Tallinn")));
  }

  @Test
  void keepsTheConsentDocumentForThePersonToSign() {
    given(repository.findByUserId(userId)).willReturn(Optional.empty());
    given(consentServiceClient.requestConsent(person.getPersonalCode(), Locale.ENGLISH))
        .willReturn(new ConsentDocument(CONFIRM_REFERENCE, CONSENT_PDF));
    given(repository.save(any())).willAnswer(saved(ID));

    assertThat(service.create(person, Locale.ENGLISH))
        .isEqualTo(new TaxDataConsentResponse(ID, AWAITING_SIGNATURE));
  }

  @Test
  void aPersonWhoAlreadyApprovedTheConsentElsewhereHasNothingToSign() {
    given(repository.findByUserId(userId)).willReturn(Optional.empty());
    given(consentServiceClient.requestConsent(person.getPersonalCode(), Locale.ENGLISH))
        .willReturn(new ConsentAlreadyApproved());
    given(consentServiceClient.findReference(person.getPersonalCode()))
        .willReturn(Optional.of(CONSENT_REFERENCE));
    given(repository.save(any())).willAnswer(saved(ID));

    assertThat(service.create(person, Locale.ENGLISH))
        .isEqualTo(new TaxDataConsentResponse(ID, APPROVED));
  }

  @Test
  void askingAgainAfterAnInvalidConsentStartsTheSameConsentOver() {
    var invalid = approved(ID, userId);
    invalid.invalidate();
    given(repository.findByUserId(userId)).willReturn(Optional.of(invalid));
    given(consentServiceClient.requestConsent(person.getPersonalCode(), Locale.ENGLISH))
        .willReturn(new ConsentDocument(CONFIRM_REFERENCE, CONSENT_PDF));
    given(repository.save(invalid)).willReturn(invalid);

    assertThat(service.create(person, Locale.ENGLISH))
        .isEqualTo(new TaxDataConsentResponse(ID, AWAITING_SIGNATURE));
    assertThat(invalid.getConsentDocument()).isEqualTo(CONSENT_PDF);
  }

  @Test
  void askingAgainWhileTheConsentAwaitsItsSignatureKeepsTheSameDocument() {
    given(repository.findByUserId(userId)).willReturn(Optional.of(awaitingSignature(ID, userId)));

    assertThat(service.create(person, Locale.ENGLISH))
        .isEqualTo(new TaxDataConsentResponse(ID, AWAITING_SIGNATURE));
    then(consentServiceClient).shouldHaveNoInteractions();
  }

  @Test
  void askingAgainAfterApprovalKeepsTheApprovedConsent() {
    given(repository.findByUserId(userId)).willReturn(Optional.of(approved(ID, userId)));

    assertThat(service.create(person, Locale.ENGLISH))
        .isEqualTo(new TaxDataConsentResponse(ID, APPROVED));
    then(consentServiceClient).shouldHaveNoInteractions();
  }

  @Test
  void aDocumentLeftUnsignedLongerThanTheConsentServiceAcceptsIsReplaced() {
    var stale = awaitingSignature(ID, userId);
    given(repository.findByUserId(userId)).willReturn(Optional.of(stale));
    service =
        new TaxDataConsentService(
            repository,
            consentServiceClient,
            incomeStore,
            Clock.fixed(NOW.plus(Duration.ofHours(25)), ZoneId.of("Europe/Tallinn")));
    given(consentServiceClient.requestConsent(person.getPersonalCode(), Locale.ENGLISH))
        .willReturn(new ConsentDocument("another-confirm-reference", CONSENT_PDF));
    given(repository.save(stale)).willReturn(stale);

    assertThat(service.create(person, Locale.ENGLISH))
        .isEqualTo(new TaxDataConsentResponse(ID, AWAITING_SIGNATURE));
    assertThat(stale.confirmReferenceOrThrow()).isEqualTo("another-confirm-reference");
  }

  @Test
  void sendsTheSignedContainerForApprovalAndKeepsTheReference() {
    givenConsents(signed(ID, userId), signed(ID, userId));
    given(repository.claimLease(ID, NOW, leaseUntil(NOW))).willReturn(1);
    given(consentServiceClient.findReference(person.getPersonalCode()))
        .willReturn(Optional.empty(), Optional.of(CONSENT_REFERENCE));
    given(repository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

    assertThat(service.completeApproval(ID, person)).isEqualTo(APPROVED);
    then(consentServiceClient).should().approve(CONFIRM_REFERENCE, SIGNED_CONTAINER);
  }

  @Test
  void aConsentTheConsentServiceAlreadyApprovedIsNotSentAgain() {
    givenConsents(signed(ID, userId), signed(ID, userId));
    given(repository.claimLease(ID, NOW, leaseUntil(NOW))).willReturn(1);
    given(consentServiceClient.findReference(person.getPersonalCode()))
        .willReturn(Optional.of(CONSENT_REFERENCE));
    given(repository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

    assertThat(service.completeApproval(ID, person)).isEqualTo(APPROVED);
    then(consentServiceClient).should(never()).approve(any(), any());
  }

  @Test
  void aRejectedSignatureInvalidatesTheConsent() {
    givenConsents(signed(ID, userId), signed(ID, userId));
    given(repository.claimLease(ID, NOW, leaseUntil(NOW))).willReturn(1);
    given(consentServiceClient.findReference(person.getPersonalCode()))
        .willReturn(Optional.empty());
    willThrow(new ConsentRejectedException("rejected"))
        .given(consentServiceClient)
        .approve(CONFIRM_REFERENCE, SIGNED_CONTAINER);
    given(repository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

    assertThat(service.completeApproval(ID, person)).isEqualTo(INVALID);
    then(incomeStore).should().deleteAll(userId);
  }

  @Test
  void anApprovalTheConsentServiceCouldNotFinishIsTriedAgainOnTheNextPoll() {
    givenConsents(signed(ID, userId));
    given(repository.claimLease(ID, NOW, leaseUntil(NOW))).willReturn(1);
    given(consentServiceClient.findReference(person.getPersonalCode()))
        .willThrow(new ConsentServiceUnavailableException("down"));

    assertThat(service.completeApproval(ID, person)).isEqualTo(SIGNED);
    then(repository).should().releaseLease(ID, leaseUntil(NOW));
  }

  @Test
  void anotherRequestAlreadyApprovingTheConsentIsLeftToFinish() {
    givenConsents(signed(ID, userId));
    given(repository.claimLease(ID, NOW, leaseUntil(NOW))).willReturn(0);

    assertThat(service.completeApproval(ID, person)).isEqualTo(SIGNED);
    then(consentServiceClient).shouldHaveNoInteractions();
  }

  @Test
  void aConsentAwaitingItsSignatureHasNothingToApprove() {
    givenConsents(awaitingSignature(ID, userId));

    assertThat(service.completeApproval(ID, person)).isEqualTo(AWAITING_SIGNATURE);
    then(consentServiceClient).shouldHaveNoInteractions();
  }

  @Test
  void anotherPersonsConsentIsNotFound() {
    given(repository.findByIdAndUserId(ID, userId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.get(ID, person)).isInstanceOf(NotFoundException.class);
  }

  @Test
  void aRepresentativeCannotConsentForSomeoneElse() {
    var representative =
        authenticatedPersonFromUser(sampleUser().build())
            .role(new Role(RoleType.LEGAL_ENTITY, "12345678", "Company OÜ"))
            .build();

    assertThatThrownBy(() -> service.create(representative, Locale.ENGLISH))
        .isInstanceOf(NotActingAsSelfException.class);
  }

  @Test
  void anApprovedConsentHasNoDocumentToShow() {
    givenConsents(approved(ID, userId));

    assertThatThrownBy(() -> service.document(ID, person)).isInstanceOf(NotFoundException.class);
  }

  private void givenConsents(TaxDataConsent first, TaxDataConsent... next) {
    given(repository.findByIdAndUserId(ID, userId)).willReturn(Optional.of(first), wrap(next));
  }

  @SuppressWarnings("unchecked")
  private static Optional<TaxDataConsent>[] wrap(TaxDataConsent... consents) {
    return Arrays.stream(consents).map(Optional::of).toArray(Optional[]::new);
  }

  private static Answer<TaxDataConsent> saved(Long id) {
    return invocation -> {
      TaxDataConsent consent = invocation.getArgument(0);
      ReflectionTestUtils.setField(consent, "id", id);
      return consent;
    };
  }
}
