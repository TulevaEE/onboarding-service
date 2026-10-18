package ee.tuleva.onboarding.insurance;

import static ee.tuleva.onboarding.auth.mobileid.MobileIDSession.PHONE_NUMBER;
import static ee.tuleva.onboarding.signature.SignatureFile.SignatureFileType.PDF;
import static ee.tuleva.onboarding.signature.SignatureStatus.OUTSTANDING_TRANSACTION;
import static ee.tuleva.onboarding.signature.SignatureStatus.SIGNATURE;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import ee.tuleva.onboarding.locale.LocaleService;
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
class InsuranceTransferFormSignatureService {

  private static final String ENTITY = "Insurance transfer form";

  private final InsuranceTransferFormService formService;
  private final SignatureService signatureService;
  private final GenericSessionStore sessionStore;
  private final LocaleService localeService;

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
    return new MobileSignatureStatusResponse(status, session.getVerificationCode());
  }

  MobileSignatureResponse startMobileIdSignature(Long id, AuthenticatedPerson person) {
    List<SignatureFile> files = filesToSign(id, person);
    String phoneNumber =
        requireNonNull(
            person.getAttribute(PHONE_NUMBER),
            "Phone number missing: insuranceTransferFormId=" + id);
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
    if (formService.summary(id, person).isSigned()) {
      throw SignatureStateException.alreadySigned(ENTITY, id);
    }
    byte[] signedContainer =
        signatureService.getSignedFile(session, signable(id), command.signature());
    formService.sign(id, signedContainer, localeService.getCurrentLocale());
    return new IdCardSignatureStatusResponse(SIGNATURE);
  }

  IdCardSignatureStatusResponse getIdCardSignatureStatus(Long id, AuthenticatedPerson person) {
    if (!formService.summary(id, person).isSigned()) {
      throw SignatureStateException.notSigned(ENTITY, id);
    }
    return new IdCardSignatureStatusResponse(SIGNATURE);
  }

  private List<SignatureFile> filesToSign(Long id, AuthenticatedPerson person) {
    InsuranceTransferForm form = formService.get(id, person);
    if (form.isSigned()) {
      throw SignatureStateException.alreadySigned(ENTITY, id);
    }
    return List.of(
        new SignatureFile(
            PDF.fileName(form.getInsurer().signedFileBaseName()),
            PDF,
            InsuranceTransferFormService.filledPdfOf(form)));
  }

  private SignatureStatus finish(
      Long id, AuthenticatedPerson person, Supplier<byte @Nullable []> signedContainer) {
    if (formService.summary(id, person).isSigned()) {
      return SIGNATURE;
    }
    byte[] container = signedContainer.get();
    if (container == null) {
      return OUTSTANDING_TRANSACTION;
    }
    formService.sign(id, container, localeService.getCurrentLocale());
    return SIGNATURE;
  }

  private static SignableEntity signable(Long id) {
    return new SignableEntity(ENTITY, id);
  }
}
