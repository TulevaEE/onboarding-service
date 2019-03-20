package ee.tuleva.onboarding.mandate;

import com.codeborne.security.mobileid.IdCardSignatureSession;
import com.codeborne.security.mobileid.MobileIDSession;
import com.codeborne.security.mobileid.MobileIdSignatureSession;
import com.codeborne.security.mobileid.SignatureFile;
import com.fasterxml.jackson.annotation.JsonView;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import ee.tuleva.onboarding.auth.smartid.SmartIdSession;
import ee.tuleva.onboarding.error.ValidationErrorsException;
import ee.tuleva.onboarding.mandate.command.CreateMandateCommand;
import ee.tuleva.onboarding.mandate.command.FinishIdCardSignCommand;
import ee.tuleva.onboarding.mandate.command.StartIdCardSignCommand;
import ee.tuleva.onboarding.mandate.exception.MandateNotFoundException;
import ee.tuleva.onboarding.mandate.exception.IdSessionException;
import ee.tuleva.onboarding.mandate.response.IdCardSignatureResponse;
import ee.tuleva.onboarding.mandate.response.MandateSignatureStatusResponse;
import ee.tuleva.onboarding.mandate.response.MobileSignatureResponse;
import ee.tuleva.onboarding.mandate.signature.SmartIdSignatureSession;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.*;
import springfox.documentation.annotations.ApiIgnore;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static ee.tuleva.onboarding.mandate.MandateController.MANDATES_URI;
import static org.springframework.web.bind.annotation.RequestMethod.*;

@Slf4j
@RestController
@RequestMapping("/v1" + MANDATES_URI)
@RequiredArgsConstructor
public class MandateController {

    public static final String MANDATES_URI = "/mandates";

    private final MandateRepository mandateRepository;
    private final MandateService mandateService;
    private final GenericSessionStore genericSessionStore;
    private final SignatureFileArchiver signatureFileArchiver;
    private final MandateFileService mandateFileService;

    @ApiOperation(value = "Create a mandate")
    @RequestMapping(method = POST)
    @JsonView(MandateView.Default.class)
    public Mandate create(@ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
                          @Valid @RequestBody CreateMandateCommand createMandateCommand,
                          @ApiIgnore Errors errors) throws ValidationErrorsException {
        if (errors.hasErrors()) {
            log.info("Create mandate command is not valid: {}", errors);
            throw new ValidationErrorsException(errors);
        }

        log.info("Creating mandate with {}", createMandateCommand);
        return mandateService.save(authenticatedPerson.getUserId(), createMandateCommand);
    }

    @ApiOperation(value = "Start signing mandate with mobile ID")
    @RequestMapping(method = PUT, value = "/{id}/signature/mobileId")
    public MobileSignatureResponse startMobileIdSignature(@PathVariable("id") Long mandateId,
                                                          @ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {

        Optional<MobileIDSession> session = genericSessionStore.get(MobileIDSession.class);
        MobileIDSession loginSession = session
                .orElseThrow(IdSessionException::mobileSessionNotFound);

        MobileIdSignatureSession signatureSession = mandateService.mobileIdSign(mandateId, authenticatedPerson.getUserId(), loginSession.phoneNumber);
        genericSessionStore.save(signatureSession);

        return new MobileSignatureResponse(signatureSession.challenge, signatureSession.challenge);
    }

    @ApiOperation(value = "Is mandate successfully signed with mobile ID")
    @RequestMapping(method = GET, value = "/{id}/signature/mobileId/status")
    public MandateSignatureStatusResponse getMobileIdSignatureStatus(@PathVariable("id") Long mandateId,
                                                                     @ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
                                                                     @RequestHeader("x-statistics-identifier") UUID statisticsIdentifier) {

        Optional<MobileIdSignatureSession> signatureSession = genericSessionStore.get(MobileIdSignatureSession.class);
        MobileIdSignatureSession session = signatureSession
                .orElseThrow(IdSessionException::mobileSignatureSessionNotFound);

        String statusCode = mandateService.finalizeMobileIdSignature(authenticatedPerson.getUserId(), statisticsIdentifier, mandateId, session);

        return new MandateSignatureStatusResponse(statusCode);
    }

    @ApiOperation(value = "Start signing mandate with Smart ID")
    @RequestMapping(method = PUT, value = "/{id}/signature/smartId")
    public MobileSignatureResponse startSmartIdSignature(@PathVariable("id") Long mandateId,
                                                         @ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
        SmartIdSignatureSession signatureSession = mandateService.smartIdSign(mandateId, authenticatedPerson.getUserId());
        genericSessionStore.save(signatureSession);

        return new MobileSignatureResponse(signatureSession.getChallengeCode(), signatureSession.getChallengeCode());
    }

    @ApiOperation(value = "Is mandate successfully signed with Smart ID")
    @RequestMapping(method = GET, value = "/{id}/signature/smartId/status")
    public MandateSignatureStatusResponse getSmartIdSignatureStatus(@PathVariable("id") Long mandateId,
                                                                    @ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
                                                                    @RequestHeader("x-statistics-identifier") UUID statisticsIdentifier) {

        Optional<SmartIdSignatureSession> signatureSession = genericSessionStore.get(SmartIdSignatureSession.class);
        SmartIdSignatureSession session = signatureSession
                .orElseThrow(IdSessionException::smartIdSignatureSessionNotFound);

        String statusCode = mandateService.finalizeSmartIdSignature(authenticatedPerson.getUserId(), statisticsIdentifier, mandateId, session);

        return new MandateSignatureStatusResponse(statusCode);
    }

    @ApiOperation(value = "Start signing mandate with ID card")
    @RequestMapping(method = PUT, value = "/{id}/signature/idCard")
    public IdCardSignatureResponse startIdCardSign(@PathVariable("id") Long mandateId,
                                                   @ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
                                                   @Valid @RequestBody StartIdCardSignCommand signCommand) {

        IdCardSignatureSession signatureSession = mandateService.idCardSign(mandateId, authenticatedPerson.getUserId(), signCommand.getClientCertificate());

        genericSessionStore.save(signatureSession);

        return new IdCardSignatureResponse(signatureSession.hash);
    }

    @ApiOperation(value = "Is mandate successfully signed with ID card")
    @RequestMapping(method = PUT, value = "/{id}/signature/idCard/status")
    public MandateSignatureStatusResponse getIdCardSignatureStatus(@PathVariable("id") Long mandateId,
                                                                   @Valid @RequestBody FinishIdCardSignCommand signCommand,
                                                                   @ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
                                                                   @RequestHeader(value = "x-statistics-identifier", required = false) UUID statisticsIdentifier) {

        Optional<IdCardSignatureSession> signatureSession = genericSessionStore.get(IdCardSignatureSession.class);
        IdCardSignatureSession session = signatureSession
                .orElseThrow(IdSessionException::cardSignatureSessionNotFound);

        String statusCode = mandateService.finalizeIdCardSignature(authenticatedPerson.getUserId(), statisticsIdentifier, mandateId, session, signCommand.getSignedHash());

        return new MandateSignatureStatusResponse(statusCode);
    }

    @ApiOperation(value = "Get mandate file")
    @RequestMapping(method = GET, value = "/{id}/file")
    public void getMandateFile(@PathVariable("id") Long mandateId,
                               @ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
                               HttpServletResponse response) throws IOException {

        Mandate mandate = getMandateOrThrow(mandateId, authenticatedPerson.getUserId());
        response.addHeader("Content-Disposition", "attachment; filename=Tuleva_avaldus.bdoc");

        byte[] content = mandate.getMandate().orElseThrow(() -> new RuntimeException("Mandate is not signed"));

        IOUtils.copy(new ByteArrayInputStream(content), response.getOutputStream());
        response.flushBuffer();

    }

    @ApiOperation(value = "Get mandate file")
    @RequestMapping(method = GET, value = "/{id}/file/preview", produces = "application/zip")
    public void getMandateFilePreview(@PathVariable("id") Long mandateId,
                                      @ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
                                      HttpServletResponse response) throws IOException {

        List<SignatureFile> files = mandateFileService.getMandateFiles(mandateId, authenticatedPerson.getUserId());
        response.addHeader("Content-Disposition", "attachment; filename=Tuleva_avaldus.zip");

        signatureFileArchiver.writeSignatureFilesToZipOutputStream(files, response.getOutputStream());
        response.flushBuffer();

    }

    private Mandate getMandateOrThrow(Long mandateId, Long userId) {
        Mandate mandate = mandateRepository.findByIdAndUserId(mandateId, userId);

        if (mandate == null) {
            throw new MandateNotFoundException();
        }

        return mandate;
    }

}
