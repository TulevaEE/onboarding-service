package ee.tuleva.onboarding.mandate;

import static ee.tuleva.onboarding.mandate.MandateController.MANDATES_URI;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;
import static org.springframework.web.bind.annotation.RequestMethod.PUT;

import com.fasterxml.jackson.annotation.JsonView;
import ee.tuleva.onboarding.auth.mobileid.MobileIDSession;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import ee.tuleva.onboarding.error.ValidationErrorsException;
import ee.tuleva.onboarding.mandate.command.CreateMandateCommand;
import ee.tuleva.onboarding.mandate.command.FinishIdCardSignCommand;
import ee.tuleva.onboarding.mandate.command.StartIdCardSignCommand;
import ee.tuleva.onboarding.mandate.exception.IdSessionException;
import ee.tuleva.onboarding.mandate.exception.NotFoundException;
import ee.tuleva.onboarding.mandate.response.IdCardSignatureResponse;
import ee.tuleva.onboarding.mandate.response.IdCardSignatureStatusResponse;
import ee.tuleva.onboarding.mandate.response.MobileSignatureResponse;
import ee.tuleva.onboarding.mandate.response.MobileSignatureStatusResponse;
import ee.tuleva.onboarding.mandate.signature.SignatureFile;
import ee.tuleva.onboarding.mandate.signature.idcard.IdCardSignatureSession;
import ee.tuleva.onboarding.mandate.signature.mobileid.MobileIdSignatureSession;
import ee.tuleva.onboarding.mandate.signature.smartid.SmartIdSignatureSession;
import io.swagger.annotations.ApiOperation;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.LocaleResolver;
import springfox.documentation.annotations.ApiIgnore;

@Slf4j
@RestController
@RequestMapping("/v1" + MANDATES_URI)
@RequiredArgsConstructor
public class MandateController {

  public static final String MANDATES_URI = "/mandates";

  private final MandateRepository mandateRepository;
  private final MandateService mandateService;
  private final GenericSessionStore sessionStore;
  private final SignatureFileArchiver signatureFileArchiver;
  private final MandateFileService mandateFileService;
  private final LocaleResolver localeResolver;

  @ApiOperation(value = "Create a mandate")
  @RequestMapping(method = POST)
  @JsonView(MandateView.Default.class)
  public Mandate create(
      @ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      @Valid @RequestBody CreateMandateCommand createMandateCommand,
      @ApiIgnore Errors errors) {
    if (errors.hasErrors()) {
      log.info("Create mandate command is not valid: {}", errors);
      throw new ValidationErrorsException(errors);
    }

    log.info("Creating mandate");
    return mandateService.save(authenticatedPerson.getUserId(), createMandateCommand);
  }

  @ApiOperation(value = "Start signing mandate with mobile ID")
  @RequestMapping(method = PUT, value = "/{id}/signature/mobileId")
  public MobileSignatureResponse startMobileIdSignature(
      @PathVariable("id") Long mandateId,
      @ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {

    Optional<MobileIDSession> session = sessionStore.get(MobileIDSession.class);
    if (session.isEmpty()) {
      log.error("Mobile session not found. user_id: {}", authenticatedPerson.getUserId());
    }
    MobileIDSession loginSession = session.orElseThrow(IdSessionException::mobileSessionNotFound);

    MobileIdSignatureSession signatureSession =
        mandateService.mobileIdSign(
            mandateId, authenticatedPerson.getUserId(), loginSession.getPhoneNumber());
    sessionStore.save(signatureSession);

    return new MobileSignatureResponse(signatureSession.getVerificationCode());
  }

  @ApiOperation(value = "Is mandate successfully signed with mobile ID")
  @RequestMapping(method = GET, value = "/{id}/signature/mobileId/status")
  public MobileSignatureStatusResponse getMobileIdSignatureStatus(
      @PathVariable("id") Long mandateId,
      @ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      @ApiIgnore HttpServletRequest request) {

    Optional<MobileIdSignatureSession> signatureSession =
        sessionStore.get(MobileIdSignatureSession.class);
    MobileIdSignatureSession session =
        signatureSession.orElseThrow(IdSessionException::mobileSignatureSessionNotFound);

    Locale locale = localeResolver.resolveLocale(request);

    String statusCode =
        mandateService.finalizeMobileIdSignature(
            authenticatedPerson.getUserId(), mandateId, session, locale);

    return new MobileSignatureStatusResponse(statusCode, session.getVerificationCode());
  }

  @ApiOperation(value = "Start signing mandate with Smart ID")
  @RequestMapping(method = PUT, value = "/{id}/signature/smartId")
  public MobileSignatureResponse startSmartIdSignature(
      @PathVariable("id") Long mandateId,
      @ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    SmartIdSignatureSession signatureSession =
        mandateService.smartIdSign(mandateId, authenticatedPerson.getUserId());
    sessionStore.save(signatureSession);

    return new MobileSignatureResponse(signatureSession.getVerificationCode());
  }

  @ApiOperation(value = "Is mandate successfully signed with Smart ID")
  @RequestMapping(method = GET, value = "/{id}/signature/smartId/status")
  public MobileSignatureStatusResponse getSmartIdSignatureStatus(
      @PathVariable("id") Long mandateId,
      @ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      HttpServletRequest request) {

    Optional<SmartIdSignatureSession> signatureSession =
        sessionStore.get(SmartIdSignatureSession.class);
    SmartIdSignatureSession session =
        signatureSession.orElseThrow(IdSessionException::smartIdSignatureSessionNotFound);

    Locale locale = localeResolver.resolveLocale(request);

    String statusCode =
        mandateService.finalizeSmartIdSignature(
            authenticatedPerson.getUserId(), mandateId, session, locale);

    return new MobileSignatureStatusResponse(statusCode, session.getVerificationCode());
  }

  @ApiOperation(value = "Start signing mandate with ID card")
  @RequestMapping(method = PUT, value = "/{id}/signature/idCard")
  public IdCardSignatureResponse startIdCardSign(
      @PathVariable("id") Long mandateId,
      @ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      @Valid @RequestBody StartIdCardSignCommand signCommand) {

    IdCardSignatureSession signatureSession =
        mandateService.idCardSign(
            mandateId, authenticatedPerson.getUserId(), signCommand.getClientCertificate());

    sessionStore.save(signatureSession);

    return new IdCardSignatureResponse(signatureSession.getHashToSignInHex());
  }

  @ApiOperation(value = "Is mandate successfully signed with ID card")
  @RequestMapping(method = PUT, value = "/{id}/signature/idCard/status")
  public IdCardSignatureStatusResponse getIdCardSignatureStatus(
      @PathVariable("id") Long mandateId,
      @Valid @RequestBody FinishIdCardSignCommand signCommand,
      @ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      HttpServletRequest request) {

    Optional<IdCardSignatureSession> signatureSession =
        sessionStore.get(IdCardSignatureSession.class);
    IdCardSignatureSession session =
        signatureSession.orElseThrow(IdSessionException::cardSignatureSessionNotFound);

    Locale locale = localeResolver.resolveLocale(request);

    String statusCode =
        mandateService.finalizeIdCardSignature(
            authenticatedPerson.getUserId(),
            mandateId,
            session,
            signCommand.getSignedHash(),
            locale);

    return new IdCardSignatureStatusResponse(statusCode);
  }

  @ApiOperation(value = "Get mandate file")
  @RequestMapping(method = GET, value = "/{id}/file")
  public void getMandateFile(
      @PathVariable("id") Long mandateId,
      @ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      HttpServletResponse response)
      throws IOException {

    Mandate mandate = getMandateOrThrow(mandateId, authenticatedPerson.getUserId());
    response.addHeader("Content-Disposition", "attachment; filename=Tuleva_avaldus.bdoc");

    byte[] content =
        mandate.getMandate().orElseThrow(() -> new RuntimeException("Mandate is not signed"));

    IOUtils.copy(new ByteArrayInputStream(content), response.getOutputStream());
    response.flushBuffer();
  }

  @ApiOperation(value = "Get mandate file")
  @RequestMapping(method = GET, value = "/{id}/file/preview", produces = "application/zip")
  public void getMandateFilePreview(
      @PathVariable("id") Long mandateId,
      @ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
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
