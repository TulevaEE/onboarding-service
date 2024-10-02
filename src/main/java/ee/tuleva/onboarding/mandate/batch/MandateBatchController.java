package ee.tuleva.onboarding.mandate.batch;

import static ee.tuleva.onboarding.mandate.batch.MandateBatchController.MANDATE_BATCHES_URI;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import ee.tuleva.onboarding.mandate.exception.IdSessionException;
import ee.tuleva.onboarding.mandate.response.MobileSignatureResponse;
import ee.tuleva.onboarding.mandate.response.MobileSignatureStatusResponse;
import ee.tuleva.onboarding.mandate.signature.smartid.SmartIdSignatureSession;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.LocaleResolver;

@Slf4j
@RestController
@RequestMapping("/v1" + MANDATE_BATCHES_URI)
@RequiredArgsConstructor
public class MandateBatchController {
  public static final String MANDATE_BATCHES_URI = "/mandate-batches";

  private final MandateBatchService mandateBatchService;
  private final GenericSessionStore sessionStore;
  private final LocaleResolver localeResolver;

  @Operation(summary = "Create mandate batch")
  @PostMapping()
  public MandateBatchDto createMandateBatch(
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      @Valid @RequestBody MandateBatchDto mandateBatchDto) {

    return MandateBatchDto.from(
        mandateBatchService.createMandateBatch(authenticatedPerson, mandateBatchDto));
  }

  @Operation(summary = "Start signing mandate batch with Smart ID")
  @PutMapping("/{id}/signature/smartId")
  public MobileSignatureResponse startSmartIdSignature(
      @PathVariable("id") Long mandateBatchId,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    SmartIdSignatureSession signatureSession =
        mandateBatchService.smartIdSign(mandateBatchId, authenticatedPerson.getUserId());
    sessionStore.save(signatureSession);

    return new MobileSignatureResponse(signatureSession.getVerificationCode());
  }

  @Operation(summary = "Is mandate batch successfully signed with Smart ID")
  @GetMapping("/{id}/signature/smartId/status")
  public MobileSignatureStatusResponse getSmartIdSignatureStatus(
      @PathVariable("id") Long mandateBatchId,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      HttpServletRequest request) {

    Optional<SmartIdSignatureSession> signatureSession =
        sessionStore.get(SmartIdSignatureSession.class);
    SmartIdSignatureSession session =
        signatureSession.orElseThrow(IdSessionException::smartIdSignatureSessionNotFound);

    Locale locale = localeResolver.resolveLocale(request);

    MandateBatchSignatureStatus statusCode =
        mandateBatchService.finalizeSmartIdSignature(
            authenticatedPerson.getUserId(), mandateBatchId, session, locale);

    // TODO status as string?
    return new MobileSignatureStatusResponse(statusCode.toString(), session.getVerificationCode());
  }
}
