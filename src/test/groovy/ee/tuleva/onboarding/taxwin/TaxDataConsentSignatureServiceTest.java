package ee.tuleva.onboarding.taxwin;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.authenticatedPersonFromUser;
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.signature.SignatureStatus.OUTSTANDING_TRANSACTION;
import static ee.tuleva.onboarding.signature.SignatureStatus.SIGNATURE;
import static ee.tuleva.onboarding.taxwin.TaxDataConsentFixture.CONSENT_PDF;
import static ee.tuleva.onboarding.taxwin.TaxDataConsentFixture.SIGNED_CONTAINER;
import static ee.tuleva.onboarding.taxwin.TaxDataConsentFixture.approved;
import static ee.tuleva.onboarding.taxwin.TaxDataConsentFixture.awaitingSignature;
import static ee.tuleva.onboarding.taxwin.TaxDataConsentFixture.signed;
import static ee.tuleva.onboarding.taxwin.TaxDataConsentStatus.APPROVED;
import static ee.tuleva.onboarding.taxwin.TaxDataConsentStatus.INVALID;
import static ee.tuleva.onboarding.taxwin.TaxDataConsentStatus.SIGNED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import ee.tuleva.onboarding.signature.SignableEntity;
import ee.tuleva.onboarding.signature.SignatureService;
import ee.tuleva.onboarding.signature.SignatureStateException;
import ee.tuleva.onboarding.signature.SmartIdSignatureSession;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaxDataConsentSignatureServiceTest {

  private static final Long ID = 7L;
  private static final SignableEntity SIGNABLE = new SignableEntity("Tax data consent", ID);

  @Mock private TaxDataConsentService consentService;
  @Mock private SignatureService signatureService;
  @Mock private GenericSessionStore sessionStore;
  @InjectMocks private TaxDataConsentSignatureService signingService;

  private final AuthenticatedPerson person =
      authenticatedPersonFromUser(sampleUser().build()).build();
  private final Long userId = person.getUserIdOrThrow();
  private final SmartIdSignatureSession session =
      new SmartIdSignatureSession(SIGNABLE, person.getPersonalCode(), List.of());

  @Test
  void signsTheConsentDocumentUnderTheFileNameTheConsentServiceRequires() {
    given(consentService.get(ID, person)).willReturn(awaitingSignature(ID, userId));
    given(
            signatureService.startSmartIdSign(
                eq(SIGNABLE),
                argThat(
                    files ->
                        files.size() == 1
                            && files.getFirst().getName().equals("Nousolek.pdf")
                            && files.getFirst().getMimeType().equals("application/pdf")
                            && Arrays.equals(files.getFirst().getContent(), CONSENT_PDF)),
                eq(person)))
        .willReturn(session);

    signingService.startSmartIdSignature(ID, person);

    then(sessionStore).should().save(session);
  }

  @Test
  void aConsentThatIsNoLongerAwaitingItsSignatureCannotBeSignedAgain() {
    given(consentService.get(ID, person)).willReturn(signed(ID, userId));

    assertThatThrownBy(() -> signingService.startSmartIdSignature(ID, person))
        .isInstanceOf(SignatureStateException.class);
  }

  @Test
  void keepsPollingUntilThePersonHasSigned() {
    given(sessionStore.get(SmartIdSignatureSession.class)).willReturn(Optional.of(session));
    given(consentService.get(ID, person)).willReturn(awaitingSignature(ID, userId));
    given(signatureService.getSignedFile(session, SIGNABLE)).willReturn(null);

    assertThat(signingService.getSmartIdSignatureStatus(ID, person).getStatusCode())
        .isEqualTo(OUTSTANDING_TRANSACTION);
    then(consentService).should(never()).markSigned(ID, person, SIGNED_CONTAINER);
  }

  @Test
  void keepsPollingWhileTheConsentServiceHasNotApprovedTheSignatureYet() {
    given(sessionStore.get(SmartIdSignatureSession.class)).willReturn(Optional.of(session));
    given(consentService.get(ID, person)).willReturn(awaitingSignature(ID, userId));
    given(signatureService.getSignedFile(session, SIGNABLE)).willReturn(SIGNED_CONTAINER);
    given(consentService.completeApproval(ID, person)).willReturn(SIGNED);

    assertThat(signingService.getSmartIdSignatureStatus(ID, person).getStatusCode())
        .isEqualTo(OUTSTANDING_TRANSACTION);
    then(consentService).should().markSigned(ID, person, SIGNED_CONTAINER);
    then(sessionStore).should(never()).remove(SmartIdSignatureSession.class);
  }

  @Test
  void finishesOnceTheConsentServiceApprovesTheSignature() {
    given(sessionStore.get(SmartIdSignatureSession.class)).willReturn(Optional.of(session));
    given(consentService.get(ID, person)).willReturn(signed(ID, userId));
    given(consentService.completeApproval(ID, person)).willReturn(APPROVED);

    assertThat(signingService.getSmartIdSignatureStatus(ID, person).getStatusCode())
        .isEqualTo(SIGNATURE);
    then(signatureService).shouldHaveNoInteractions();
    then(sessionStore).should().remove(SmartIdSignatureSession.class);
  }

  @Test
  void aSignatureTheConsentServiceRejectedEndsTheSigning() {
    given(sessionStore.get(SmartIdSignatureSession.class)).willReturn(Optional.of(session));
    given(consentService.get(ID, person)).willReturn(signed(ID, userId));
    given(consentService.completeApproval(ID, person)).willReturn(INVALID);

    assertThatThrownBy(() -> signingService.getSmartIdSignatureStatus(ID, person))
        .isInstanceOf(TaxDataConsentRejectedException.class);
  }

  @Test
  void theIdCardStatusOfAnApprovedConsentIsSigned() {
    given(consentService.get(ID, person)).willReturn(approved(ID, userId));
    given(consentService.completeApproval(ID, person)).willReturn(APPROVED);

    assertThat(signingService.getIdCardSignatureStatus(ID, person).getStatusCode())
        .isEqualTo(SIGNATURE);
  }

  @Test
  void theIdCardStatusOfAnUnsignedConsentIsAnError() {
    given(consentService.get(ID, person)).willReturn(awaitingSignature(ID, userId));

    assertThatThrownBy(() -> signingService.getIdCardSignatureStatus(ID, person))
        .isInstanceOf(SignatureStateException.class);
  }
}
