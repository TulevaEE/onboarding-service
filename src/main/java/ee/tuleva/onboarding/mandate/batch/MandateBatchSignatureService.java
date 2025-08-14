package ee.tuleva.onboarding.mandate.batch;

import static ee.tuleva.onboarding.auth.mobileid.MobileIDSession.PHONE_NUMBER;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.mandate.command.FinishIdCardSignCommand;
import ee.tuleva.onboarding.mandate.command.StartIdCardSignCommand;
import ee.tuleva.onboarding.mandate.exception.IdSessionException;
import ee.tuleva.onboarding.signature.SignatureFile;
import ee.tuleva.onboarding.signature.SignatureService;
import ee.tuleva.onboarding.signature.idcard.IdCardSignatureSession;
import ee.tuleva.onboarding.signature.mobileid.MobileIdSignatureSession;
import ee.tuleva.onboarding.signature.response.*;
import ee.tuleva.onboarding.signature.smartid.SmartIdSignatureSession;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

@Service
@RequiredArgsConstructor
public class MandateBatchSignatureService {
  private final MandateBatchService mandateBatchService;
  private final GenericSessionStore sessionStore;
  private final LocaleService localeService;
  private final UserService userService;
  private final SignatureService signService;

  public MobileSignatureResponse startSmartIdSignature(
      Long mandateBatchId, AuthenticatedPerson authenticatedPerson) {

    User user = userService.getById(authenticatedPerson.getUserId()).orElseThrow();
    List<SignatureFile> files =
        mandateBatchService.getMandateBatchContentFiles(mandateBatchId, user);

    SmartIdSignatureSession signatureSession =
        signService.startSmartIdSign(files, user.getPersonalCode());
    sessionStore.save(signatureSession);

    return new MobileSignatureResponse(null); // verificationCode is null when starting
  }

  public MobileSignatureStatusResponse getSmartIdSignatureStatus(
      Long mandateBatchId, AuthenticatedPerson authenticatedPerson) {

    Optional<SmartIdSignatureSession> signatureSession =
        sessionStore.get(SmartIdSignatureSession.class);
    SmartIdSignatureSession session =
        signatureSession.orElseThrow(IdSessionException::smartIdSignatureSessionNotFound);

    Locale locale = localeService.getCurrentLocale();

    SignatureStatus statusCode =
        mandateBatchService.finalizeMobileSignature(
            authenticatedPerson.getUserId(), mandateBatchId, session, locale);

    return new MobileSignatureStatusResponse(statusCode, session.getVerificationCode());
  }

  public IdCardSignatureResponse startIdCardSign(
      Long mandateBatchId,
      AuthenticatedPerson authenticatedPerson,
      @Valid @RequestBody StartIdCardSignCommand signCommand) {

    User user = userService.getById(authenticatedPerson.getUserId()).orElseThrow();
    List<SignatureFile> files =
        mandateBatchService.getMandateBatchContentFiles(mandateBatchId, user);

    IdCardSignatureSession signatureSession =
        signService.startIdCardSign(files, signCommand.getClientCertificate());

    sessionStore.save(signatureSession);

    return new IdCardSignatureResponse(signatureSession.getHashToSignInHex());
  }

  public IdCardSignatureStatusResponse persistIdCardSignedHashAndGetProcessingStatus(
      Long mandateBatchId,
      @Valid @RequestBody FinishIdCardSignCommand signCommand,
      AuthenticatedPerson authenticatedPerson) {

    Optional<IdCardSignatureSession> signatureSession =
        sessionStore.get(IdCardSignatureSession.class);
    IdCardSignatureSession session =
        signatureSession.orElseThrow(IdSessionException::cardSignatureSessionNotFound);

    Locale locale = localeService.getCurrentLocale();

    SignatureStatus statusCode =
        mandateBatchService.persistIdCardSignedFileOrGetBatchProcessingStatus(
            authenticatedPerson.getUserId(),
            mandateBatchId,
            session,
            signCommand.getSignedHash(),
            locale);

    return new IdCardSignatureStatusResponse(statusCode);
  }

  public MobileSignatureResponse startMobileIdSignature(
      Long mandateBatchId, AuthenticatedPerson authenticatedPerson) {

    User user = userService.getById(authenticatedPerson.getUserId()).orElseThrow();
    List<SignatureFile> files =
        mandateBatchService.getMandateBatchContentFiles(mandateBatchId, user);

    MobileIdSignatureSession signatureSession =
        signService.startMobileIdSign(
            files, user.getPersonalCode(), authenticatedPerson.getAttribute(PHONE_NUMBER));
    sessionStore.save(signatureSession);

    return new MobileSignatureResponse(signatureSession.getVerificationCode());
  }

  public MobileSignatureStatusResponse getMobileIdSignatureStatus(
      Long mandateBatchId, AuthenticatedPerson authenticatedPerson) {

    Optional<MobileIdSignatureSession> signatureSession =
        sessionStore.get(MobileIdSignatureSession.class);
    MobileIdSignatureSession session =
        signatureSession.orElseThrow(IdSessionException::mobileSignatureSessionNotFound);

    Locale locale = localeService.getCurrentLocale();

    SignatureStatus statusCode =
        mandateBatchService.finalizeMobileSignature(
            authenticatedPerson.getUserId(), mandateBatchId, session, locale);

    return new MobileSignatureStatusResponse(statusCode, session.getVerificationCode());
  }
}
