package ee.tuleva.onboarding.mandate.batch;

import static ee.tuleva.onboarding.auth.mobileid.MobileIDSession.PHONE_NUMBER;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.mandate.command.FinishIdCardSignCommand;
import ee.tuleva.onboarding.mandate.command.StartIdCardSignCommand;
import ee.tuleva.onboarding.mandate.exception.IdSessionException;
import ee.tuleva.onboarding.mandate.response.*;
import ee.tuleva.onboarding.mandate.signature.idcard.IdCardSignatureSession;
import ee.tuleva.onboarding.mandate.signature.mobileid.MobileIdSignatureSession;
import ee.tuleva.onboarding.mandate.signature.smartid.SmartIdSignatureSession;
import jakarta.validation.Valid;
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

  public MobileSignatureResponse startSmartIdSignature(
      Long mandateBatchId, AuthenticatedPerson authenticatedPerson) {
    SmartIdSignatureSession signatureSession =
        mandateBatchService.smartIdSign(mandateBatchId, authenticatedPerson.getUserId());
    sessionStore.save(signatureSession);

    return new MobileSignatureResponse(signatureSession.getVerificationCode());
  }

  public MobileSignatureStatusResponse getSmartIdSignatureStatus(
      Long mandateBatchId, AuthenticatedPerson authenticatedPerson) {

    Optional<SmartIdSignatureSession> signatureSession =
        sessionStore.get(SmartIdSignatureSession.class);
    SmartIdSignatureSession session =
        signatureSession.orElseThrow(IdSessionException::smartIdSignatureSessionNotFound);

    Locale locale = localeService.getCurrentLocale();

    MandateSignatureStatus statusCode =
        mandateBatchService.finalizeSmartIdSignature(
            authenticatedPerson.getUserId(), mandateBatchId, session, locale);

    return new MobileSignatureStatusResponse(statusCode, session.getVerificationCode());
  }

  public IdCardSignatureResponse startIdCardSign(
      Long mandateBatchId,
      AuthenticatedPerson authenticatedPerson,
      @Valid @RequestBody StartIdCardSignCommand signCommand) {

    IdCardSignatureSession signatureSession =
        mandateBatchService.idCardSign(
            mandateBatchId, authenticatedPerson.getUserId(), signCommand.getClientCertificate());

    sessionStore.save(signatureSession);

    return new IdCardSignatureResponse(signatureSession.getHashToSignInHex());
  }

  public IdCardSignatureStatusResponse getIdCardSignatureStatus(
      Long mandateBatchId,
      @Valid @RequestBody FinishIdCardSignCommand signCommand,
      AuthenticatedPerson authenticatedPerson) {

    Optional<IdCardSignatureSession> signatureSession =
        sessionStore.get(IdCardSignatureSession.class);
    IdCardSignatureSession session =
        signatureSession.orElseThrow(IdSessionException::cardSignatureSessionNotFound);

    Locale locale = localeService.getCurrentLocale();

    MandateSignatureStatus statusCode =
        mandateBatchService.finalizeIdCardSignature(
            authenticatedPerson.getUserId(),
            mandateBatchId,
            session,
            signCommand.getSignedHash(),
            locale);

    return new IdCardSignatureStatusResponse(statusCode);
  }

  public MobileSignatureResponse startMobileIdSignature(
      Long mandateBatchId, AuthenticatedPerson authenticatedPerson) {

    MobileIdSignatureSession signatureSession =
        mandateBatchService.mobileIdSign(
            mandateBatchId,
            authenticatedPerson.getUserId(),
            authenticatedPerson.getAttribute(PHONE_NUMBER));
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

    MandateSignatureStatus statusCode =
        mandateBatchService.finalizeMobileIdSignature(
            authenticatedPerson.getUserId(), mandateBatchId, session, locale);

    return new MobileSignatureStatusResponse(statusCode, session.getVerificationCode());
  }
}
