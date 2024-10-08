package ee.tuleva.onboarding.mandate.batch;

import static ee.tuleva.onboarding.auth.mobileid.MobileIDSession.PHONE_NUMBER;
import static ee.tuleva.onboarding.mandate.batch.MandateBatchController.MANDATE_BATCHES_URI;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.mandate.command.FinishIdCardSignCommand;
import ee.tuleva.onboarding.mandate.command.StartIdCardSignCommand;
import ee.tuleva.onboarding.mandate.exception.IdSessionException;
import ee.tuleva.onboarding.mandate.response.IdCardSignatureResponse;
import ee.tuleva.onboarding.mandate.response.IdCardSignatureStatusResponse;
import ee.tuleva.onboarding.mandate.response.MandateSignatureStatus;
import ee.tuleva.onboarding.mandate.response.MobileSignatureResponse;
import ee.tuleva.onboarding.mandate.response.MobileSignatureStatusResponse;
import ee.tuleva.onboarding.mandate.signature.idcard.IdCardSignatureSession;
import ee.tuleva.onboarding.mandate.signature.mobileid.MobileIdSignatureSession;
import ee.tuleva.onboarding.mandate.signature.smartid.SmartIdSignatureSession;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/v1" + MANDATE_BATCHES_URI)
@RequiredArgsConstructor
public class MandateBatchController {
  public static final String MANDATE_BATCHES_URI = "/mandate-batches";

  private final MandateBatchService mandateBatchService;
  private final GenericSessionStore sessionStore;
  private final LocaleService localeService;

  @Operation(summary = "Create mandate batch")
  @PostMapping()
  public MandateBatchDto createMandateBatch(
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      @Valid @RequestBody MandateBatchDto mandateBatchDto) {

    return MandateBatchDto.from(
        mandateBatchService.createMandateBatch(authenticatedPerson, mandateBatchDto));
  }

  @Operation(summary = "Start signing mandate batch with Smart ID")
  @PutMapping("/{id}/signature/smart-id")
  public MobileSignatureResponse startSmartIdSignature(
      @PathVariable("id") Long mandateBatchId,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    SmartIdSignatureSession signatureSession =
        mandateBatchService.smartIdSign(mandateBatchId, authenticatedPerson.getUserId());
    sessionStore.save(signatureSession);

    return new MobileSignatureResponse(signatureSession.getVerificationCode());
  }

  @Operation(summary = "Is mandate batch successfully signed with Smart ID")
  @GetMapping("/{id}/signature/smart-id/status")
  public MobileSignatureStatusResponse getSmartIdSignatureStatus(
      @PathVariable("id") Long mandateBatchId,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {

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

  @Operation(summary = "Start signing mandate batch with ID card")
  @PutMapping("/{id}/signature/id-card")
  public IdCardSignatureResponse startIdCardSign(
      @PathVariable("id") Long mandateBatchId,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      @Valid @RequestBody StartIdCardSignCommand signCommand) {

    IdCardSignatureSession signatureSession =
        mandateBatchService.idCardSign(
            mandateBatchId, authenticatedPerson.getUserId(), signCommand.getClientCertificate());

    sessionStore.save(signatureSession);

    return new IdCardSignatureResponse(signatureSession.getHashToSignInHex());
  }

  @Operation(summary = "Is mandate batch successfully signed with ID card")
  @PutMapping("/{id}/signature/id-card/status")
  public IdCardSignatureStatusResponse getIdCardSignatureStatus(
      @PathVariable("id") Long mandateBatchId,
      @Valid @RequestBody FinishIdCardSignCommand signCommand,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {

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

  @Operation(summary = "Start signing mandate batch with mobile ID")
  @PutMapping("/{id}/signature/mobile-id")
  public MobileSignatureResponse startMobileIdSignature(
      @PathVariable("id") Long mandateBatchId,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {

    MobileIdSignatureSession signatureSession =
        mandateBatchService.mobileIdSign(
            mandateBatchId,
            authenticatedPerson.getUserId(),
            authenticatedPerson.getAttribute(PHONE_NUMBER));
    sessionStore.save(signatureSession);

    return new MobileSignatureResponse(signatureSession.getVerificationCode());
  }

  @Operation(summary = "Is mandate batch successfully signed with mobile ID")
  @GetMapping("/{id}/signature/mobile-id/status")
  public MobileSignatureStatusResponse getMobileIdSignatureStatus(
      @PathVariable("id") Long mandateBatchId,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {

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
