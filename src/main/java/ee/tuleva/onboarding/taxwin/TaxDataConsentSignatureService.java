package ee.tuleva.onboarding.taxwin;

import static ee.tuleva.onboarding.auth.mobileid.MobileIDSession.PHONE_NUMBER;
import static ee.tuleva.onboarding.signature.SignatureFile.SignatureFileType.PDF;
import static ee.tuleva.onboarding.signature.SignatureStatus.OUTSTANDING_TRANSACTION;
import static ee.tuleva.onboarding.signature.SignatureStatus.SIGNATURE;
import static ee.tuleva.onboarding.taxwin.TaxDataConsentStatus.APPROVED;
import static ee.tuleva.onboarding.taxwin.TaxDataConsentStatus.AWAITING_SIGNATURE;
import static ee.tuleva.onboarding.taxwin.TaxDataConsentStatus.INVALID;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import ee.tuleva.onboarding.signature.FinishIdCardSignCommand;
import ee.tuleva.onboarding.signature.IdCardSignatureResponse;
import ee.tuleva.onboarding.signature.IdCardSignatureSession;
import ee.tuleva.onboarding.signature.IdCardSignatureStatusResponse;
import ee.tuleva.onboarding.signature.IdSessionException;
import ee.tuleva.onboarding.signature.MobileIdSignatureSession;
import ee.tuleva.onboarding.signature.MobileSignatureResponse;
import ee.tuleva.onboarding.signature.MobileSignatureStatusResponse;
import ee.tuleva.onboarding.signature.SignableEntity;
import ee.tuleva.onboarding.signature.SignatureFile;
import ee.tuleva.onboarding.signature.SignatureService;
import ee.tuleva.onboarding.signature.SignatureStateException;
import ee.tuleva.onboarding.signature.SignatureStatus;
import ee.tuleva.onboarding.signature.SmartIdSignatureSession;
import ee.tuleva.onboarding.signature.StartIdCardSignCommand;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class TaxDataConsentSignatureService {

  private static final String ENTITY = "Tax data consent";
  private static final String CONSENT_FILE_NAME = "Nousolek.pdf";

  private final TaxDataConsentService consentService;
  private final SignatureService signatureService;
  private final GenericSessionStore sessionStore;

  MobileSignatureResponse startSmartIdSignature(Long id, AuthenticatedPerson person) {
    SmartIdSignatureSession session =
        signatureService.startSmartIdSign(signable(id), filesToSign(id, person), person);
    sessionStore.save(session);
    return new MobileSignatureResponse(session.getVerificationCode());
  }

  MobileSignatureStatusResponse getSmartIdSignatureStatus(Long id, AuthenticatedPerson person) {
    SmartIdSignatureSession session =
        sessionStore
            .get(SmartIdSignatureSession.class)
            .orElseThrow(IdSessionException::smartIdSignatureSessionNotFound);
    SignatureStatus status =
        finish(id, person, () -> signatureService.getSignedFile(session, signable(id)));
    if (status == SIGNATURE) {
      sessionStore.remove(SmartIdSignatureSession.class);
    }
    return new MobileSignatureStatusResponse(status, session.getVerificationCode());
  }

  MobileSignatureResponse startMobileIdSignature(Long id, AuthenticatedPerson person) {
    List<SignatureFile> files = filesToSign(id, person);
    String phoneNumber =
        requireNonNull(
            person.getAttribute(PHONE_NUMBER), "Phone number missing: taxDataConsentId=" + id);
    MobileIdSignatureSession session =
        signatureService.startMobileIdSign(
            signable(id), files, person.getPersonalCode(), phoneNumber);
    sessionStore.save(session);
    return new MobileSignatureResponse(session.getVerificationCode());
  }

  MobileSignatureStatusResponse getMobileIdSignatureStatus(Long id, AuthenticatedPerson person) {
    MobileIdSignatureSession session =
        sessionStore
            .get(MobileIdSignatureSession.class)
            .orElseThrow(IdSessionException::mobileSignatureSessionNotFound);
    SignatureStatus status =
        finish(id, person, () -> signatureService.getSignedFile(session, signable(id)));
    if (status == SIGNATURE) {
      sessionStore.remove(MobileIdSignatureSession.class);
    }
    return new MobileSignatureStatusResponse(status, session.getVerificationCode());
  }

  IdCardSignatureResponse startIdCardSignature(
      Long id, AuthenticatedPerson person, StartIdCardSignCommand command) {
    IdCardSignatureSession session =
        signatureService.startIdCardSign(
            signable(id),
            filesToSign(id, person),
            command.certificate(),
            command.supportedHashFunctions(),
            person.getPersonalCode());
    sessionStore.save(session);
    return IdCardSignatureResponse.from(session);
  }

  IdCardSignatureStatusResponse persistIdCardSignature(
      Long id, FinishIdCardSignCommand command, AuthenticatedPerson person) {
    IdCardSignatureSession session =
        sessionStore
            .get(IdCardSignatureSession.class)
            .orElseThrow(IdSessionException::cardSignatureSessionNotFound);
    SignatureStatus status =
        finish(
            id,
            person,
            () -> signatureService.getSignedFile(session, signable(id), command.signature()));
    sessionStore.remove(IdCardSignatureSession.class);
    return new IdCardSignatureStatusResponse(status);
  }

  IdCardSignatureStatusResponse getIdCardSignatureStatus(Long id, AuthenticatedPerson person) {
    TaxDataConsent consent = consentService.get(id, person);
    if (consent.is(AWAITING_SIGNATURE)) {
      throw SignatureStateException.notSigned(ENTITY, id);
    }
    return new IdCardSignatureStatusResponse(approvalStatus(id, person));
  }

  private List<SignatureFile> filesToSign(Long id, AuthenticatedPerson person) {
    TaxDataConsent consent = consentService.get(id, person);
    if (!consent.is(AWAITING_SIGNATURE)) {
      throw SignatureStateException.alreadySigned(ENTITY, id);
    }
    return List.of(new SignatureFile(CONSENT_FILE_NAME, PDF, consent.documentOrThrow()));
  }

  private SignatureStatus finish(
      Long id, AuthenticatedPerson person, Supplier<byte @Nullable []> signedContainer) {
    TaxDataConsent consent = consentService.get(id, person);
    if (consent.is(AWAITING_SIGNATURE)) {
      byte[] container = signedContainer.get();
      if (container == null) {
        return OUTSTANDING_TRANSACTION;
      }
      consentService.markSigned(id, person, container);
    }
    return approvalStatus(id, person);
  }

  private SignatureStatus approvalStatus(Long id, AuthenticatedPerson person) {
    TaxDataConsentStatus status = consentService.completeApproval(id, person);
    if (status == INVALID) {
      throw new TaxDataConsentRejectedException(id);
    }
    return status == APPROVED ? SIGNATURE : OUTSTANDING_TRANSACTION;
  }

  private static SignableEntity signable(Long id) {
    return new SignableEntity(ENTITY, id);
  }
}
