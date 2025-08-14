package ee.tuleva.onboarding.mandate;

import static ee.tuleva.onboarding.auth.mobileid.MobileIDSession.PHONE_NUMBER;
import static ee.tuleva.onboarding.mandate.MandateController.MANDATES_URI;

import com.fasterxml.jackson.annotation.JsonView;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import ee.tuleva.onboarding.error.ValidationErrorsException;
import ee.tuleva.onboarding.mandate.command.CreateMandateCommand;
import ee.tuleva.onboarding.mandate.command.FinishIdCardSignCommand;
import ee.tuleva.onboarding.mandate.command.StartIdCardSignCommand;
import ee.tuleva.onboarding.mandate.exception.IdSessionException;
import ee.tuleva.onboarding.mandate.exception.NotFoundException;
import ee.tuleva.onboarding.mandate.generic.GenericMandateService;
import ee.tuleva.onboarding.signature.SignatureFile;
import ee.tuleva.onboarding.signature.idcard.IdCardSignatureSession;
import ee.tuleva.onboarding.signature.mobileid.MobileIdSignatureSession;
import ee.tuleva.onboarding.signature.response.*;
import ee.tuleva.onboarding.signature.smartid.SmartIdSignatureSession;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.LocaleResolver;

@Slf4j
@RestController
@RequestMapping("/v1" + MANDATES_URI)
@RequiredArgsConstructor
public class MandateController {

  public static final String MANDATES_URI = "/mandates";

  private final MandateRepository mandateRepository;
  private final MandateService mandateService;
  private final GenericMandateService genericMandateService;
  private final GenericSessionStore sessionStore;
  private final SignatureFileArchiver signatureFileArchiver;
  private final MandateFileService mandateFileService;
  private final LocaleResolver localeResolver;

  @Operation(summary = "Create a mandate")
  @PostMapping
  @JsonView(MandateView.Default.class)
  public Mandate create(
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      @Valid @RequestBody CreateMandateCommand createMandateCommand,
      @Parameter(hidden = true) Errors errors) {
    if (errors.hasErrors()) {
      log.info("Create mandate command is not valid: {}", errors);
      throw new ValidationErrorsException(errors);
    }

    log.info("Creating mandate: {}", createMandateCommand);
    return mandateService.save(authenticatedPerson, createMandateCommand);
  }

  @Operation(summary = "Start signing mandate with mobile ID")
  @PutMapping("/{id}/signature/mobileId")
  public MobileSignatureResponse startMobileIdSignature(
      @PathVariable("id") Long mandateId,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {

    MobileIdSignatureSession signatureSession =
        mandateService.mobileIdSign(
            mandateId,
            authenticatedPerson.getUserId(),
            authenticatedPerson.getAttribute(PHONE_NUMBER));
    sessionStore.save(signatureSession);

    return new MobileSignatureResponse(signatureSession.getVerificationCode());
  }

  @Operation(summary = "Is mandate successfully signed with mobile ID")
  @GetMapping("/{id}/signature/mobileId/status")
  public MobileSignatureStatusResponse getMobileIdSignatureStatus(
      @PathVariable("id") Long mandateId,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      @Parameter(hidden = true) HttpServletRequest request) {

    Optional<MobileIdSignatureSession> signatureSession =
        sessionStore.get(MobileIdSignatureSession.class);
    MobileIdSignatureSession session =
        signatureSession.orElseThrow(IdSessionException::mobileSignatureSessionNotFound);

    Locale locale = localeResolver.resolveLocale(request);

    SignatureStatus statusCode =
        mandateService.finalizeMobileIdSignature(
            authenticatedPerson.getUserId(), mandateId, session, locale);

    return new MobileSignatureStatusResponse(statusCode, session.getVerificationCode());
  }

  @Operation(summary = "Start signing mandate with Smart ID")
  @PutMapping("/{id}/signature/smartId")
  public MobileSignatureResponse startSmartIdSignature(
      @PathVariable("id") Long mandateId,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    SmartIdSignatureSession signatureSession =
        mandateService.smartIdSign(mandateId, authenticatedPerson.getUserId());
    sessionStore.save(signatureSession);

    return new MobileSignatureResponse(null); // verificationCode is null in this instance
  }

  @Operation(summary = "Is mandate successfully signed with Smart ID")
  @GetMapping("/{id}/signature/smartId/status")
  public MobileSignatureStatusResponse getSmartIdSignatureStatus(
      @PathVariable("id") Long mandateId,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      HttpServletRequest request) {

    Optional<SmartIdSignatureSession> signatureSession =
        sessionStore.get(SmartIdSignatureSession.class);
    SmartIdSignatureSession session =
        signatureSession.orElseThrow(IdSessionException::smartIdSignatureSessionNotFound);

    Locale locale = localeResolver.resolveLocale(request);

    SignatureStatus statusCode =
        mandateService.finalizeSmartIdSignature(
            authenticatedPerson.getUserId(), mandateId, session, locale);

    return new MobileSignatureStatusResponse(statusCode, session.getVerificationCode());
  }

  @Operation(summary = "Start signing mandate with ID card")
  @PutMapping("/{id}/signature/idCard")
  public IdCardSignatureResponse startIdCardSign(
      @PathVariable("id") Long mandateId,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      @Valid @RequestBody StartIdCardSignCommand signCommand) {

    IdCardSignatureSession signatureSession =
        mandateService.idCardSign(
            mandateId, authenticatedPerson.getUserId(), signCommand.getClientCertificate());

    sessionStore.save(signatureSession);

    return new IdCardSignatureResponse(signatureSession.getHashToSignInHex());
  }

  // TODO: split this into PUT and GET endpoints or migrate all logic to MandateBatch
  // Currently first call persists signed hex, and later polling calls just check if mandates have
  // been processsed
  @Operation(summary = "Is mandate successfully signed with ID card")
  @PutMapping("/{id}/signature/idCard/status")
  public IdCardSignatureStatusResponse getIdCardSignatureStatus(
      @PathVariable("id") Long mandateId,
      @Valid @RequestBody FinishIdCardSignCommand signCommand,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      HttpServletRequest request) {

    Optional<IdCardSignatureSession> signatureSession =
        sessionStore.get(IdCardSignatureSession.class);
    IdCardSignatureSession session =
        signatureSession.orElseThrow(IdSessionException::cardSignatureSessionNotFound);

    Locale locale = localeResolver.resolveLocale(request);

    SignatureStatus statusCode =
        mandateService.finalizeIdCardSignature(
            authenticatedPerson.getUserId(),
            mandateId,
            session,
            signCommand.getSignedHash(),
            locale);

    return new IdCardSignatureStatusResponse(statusCode);
  }

  @Operation(summary = "Get mandate file")
  @GetMapping("/{id}/file")
  public void getMandateFile(
      @PathVariable("id") Long mandateId,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      HttpServletResponse response)
      throws IOException {

    Mandate mandate = getMandateOrThrow(mandateId, authenticatedPerson.getUserId());
    response.addHeader("Content-Disposition", "attachment; filename=Tuleva_avaldus.bdoc");

    byte[] content =
        mandate.getMandate().orElseThrow(() -> new RuntimeException("Mandate is not signed"));

    IOUtils.copy(new ByteArrayInputStream(content), response.getOutputStream());
    response.flushBuffer();
  }

  @Operation(summary = "Get mandate file")
  @GetMapping(value = "/{id}/file/preview", produces = "application/zip")
  public void getMandateFilePreview(
      @PathVariable("id") Long mandateId,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      HttpServletResponse response)
      throws IOException {

    List<SignatureFile> files =
        mandateFileService.getMandateFiles(mandateId, authenticatedPerson.getUserId());
    response.addHeader("Content-Disposition", "attachment; filename=Tuleva_avaldus.zip");

    signatureFileArchiver.writeSignatureFilesToZipOutputStream(files, response.getOutputStream());
    response.flushBuffer();
  }

  private Mandate getMandateOrThrow(Long mandateId, Long userId) {
    Mandate mandate = mandateRepository.findByIdAndUserId(mandateId, userId);

    if (mandate == null) {
      throw new NotFoundException("Mandate not found: id=" + mandateId);
    }

    return mandate;
  }
}
