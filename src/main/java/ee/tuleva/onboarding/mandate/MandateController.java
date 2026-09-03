package ee.tuleva.onboarding.mandate;

import static ee.tuleva.onboarding.auth.mobileid.MobileIDSession.PHONE_NUMBER;
import static ee.tuleva.onboarding.mandate.MandateController.MANDATES_URI;
import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonView;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import ee.tuleva.onboarding.error.NotFoundException;
import ee.tuleva.onboarding.error.ValidationErrorsException;
import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.mandate.command.CreateMandateCommand;
import ee.tuleva.onboarding.mandate.generic.GenericMandateService;
import ee.tuleva.onboarding.signature.*;
import ee.tuleva.onboarding.signature.FinishIdCardSignCommand;
import ee.tuleva.onboarding.signature.IdCardSignatureSession;
import ee.tuleva.onboarding.signature.IdSessionException;
import ee.tuleva.onboarding.signature.MobileIdSignatureSession;
import ee.tuleva.onboarding.signature.SignatureFile;
import ee.tuleva.onboarding.signature.SmartIdSignatureSession;
import ee.tuleva.onboarding.signature.StartIdCardSignCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/v1" + MANDATES_URI)
@RequiredArgsConstructor
public class MandateController implements SignatureController<Long> {

  public static final String MANDATES_URI = "/mandates";

  private final MandateRepository mandateRepository;
  private final MandateService mandateService;
  private final GenericMandateService genericMandateService;
  private final GenericSessionStore sessionStore;
  private final SignatureFileArchiver signatureFileArchiver;
  private final MandateFileService mandateFileService;
  private final LocaleService localeService;

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

  @Override
  @Operation(summary = "Start signing mandate with mobile ID")
  public MobileSignatureResponse startMobileIdSignature(
      @PathVariable("id") Long mandateId,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    MobileIdSignatureSession signatureSession =
        mandateService.mobileIdSign(
            mandateId,
            authenticatedPerson.getUserIdOrThrow(),
            requireNonNull(
                authenticatedPerson.getAttribute(PHONE_NUMBER),
                "Phone number missing: mandateId=" + mandateId));
    sessionStore.save(signatureSession);

    return new MobileSignatureResponse(signatureSession.getVerificationCode());
  }

  @Override
  @Operation(summary = "Is mandate successfully signed with mobile ID")
  public MobileSignatureStatusResponse getMobileIdSignatureStatus(
      @PathVariable("id") Long mandateId,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    MobileIdSignatureSession session =
        sessionStore
            .get(MobileIdSignatureSession.class)
            .orElseThrow(IdSessionException::mobileSignatureSessionNotFound);

    SignatureStatus statusCode =
        mandateService.finalizeMobileIdSignature(
            authenticatedPerson.getUserIdOrThrow(),
            mandateId,
            session,
            localeService.getCurrentLocale());

    return new MobileSignatureStatusResponse(statusCode, session.getVerificationCode());
  }

  @Override
  @Operation(summary = "Start signing mandate with Smart ID")
  public MobileSignatureResponse startSmartIdSignature(
      @PathVariable("id") Long mandateId,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    SmartIdSignatureSession signatureSession =
        mandateService.smartIdSign(mandateId, authenticatedPerson.getUserIdOrThrow());
    sessionStore.save(signatureSession);

    return new MobileSignatureResponse(signatureSession.getVerificationCode());
  }

  @Override
  @Operation(summary = "Is mandate successfully signed with Smart ID")
  public MobileSignatureStatusResponse getSmartIdSignatureStatus(
      @PathVariable("id") Long mandateId,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    SmartIdSignatureSession session =
        sessionStore
            .get(SmartIdSignatureSession.class)
            .orElseThrow(IdSessionException::smartIdSignatureSessionNotFound);

    SignatureStatus statusCode =
        mandateService.finalizeSmartIdSignature(
            authenticatedPerson.getUserIdOrThrow(),
            mandateId,
            session,
            localeService.getCurrentLocale());

    return new MobileSignatureStatusResponse(statusCode, session.getVerificationCode());
  }

  @Override
  @Operation(summary = "Start signing mandate with ID card")
  public IdCardSignatureResponse startIdCardSignature(
      @PathVariable("id") Long mandateId,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      @Valid @RequestBody StartIdCardSignCommand signCommand) {
    IdCardSignatureSession signatureSession =
        mandateService.idCardSign(
            mandateId, authenticatedPerson.getUserIdOrThrow(), signCommand.certificate());
    sessionStore.save(signatureSession);

    return IdCardSignatureResponse.from(signatureSession);
  }

  @Override
  @Operation(summary = "Persist the ID card signature of the mandate and start processing it")
  public IdCardSignatureStatusResponse persistIdCardSignature(
      @PathVariable("id") Long mandateId,
      @Valid @RequestBody FinishIdCardSignCommand signCommand,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    IdCardSignatureSession session =
        sessionStore
            .get(IdCardSignatureSession.class)
            .orElseThrow(IdSessionException::cardSignatureSessionNotFound);

    SignatureStatus statusCode =
        mandateService.persistIdCardSignature(
            authenticatedPerson.getUserIdOrThrow(), mandateId, session, signCommand.signature());

    return new IdCardSignatureStatusResponse(statusCode);
  }

  @Override
  @Operation(summary = "Is the mandate signed with ID card processed")
  public IdCardSignatureStatusResponse getIdCardSignatureStatus(
      @PathVariable("id") Long mandateId,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    SignatureStatus statusCode =
        mandateService.getIdCardSignatureStatus(
            authenticatedPerson.getUserIdOrThrow(), mandateId, localeService.getCurrentLocale());

    return new IdCardSignatureStatusResponse(statusCode);
  }

  @Operation(summary = "Get mandate file")
  @GetMapping("/{id}/file")
  public void getMandateFile(
      @PathVariable("id") Long mandateId,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      HttpServletResponse response)
      throws IOException {

    Mandate mandate = getMandateOrThrow(mandateId, authenticatedPerson.getUserIdOrThrow());
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
        mandateFileService.getMandateFiles(mandateId, authenticatedPerson.getUserIdOrThrow());
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
