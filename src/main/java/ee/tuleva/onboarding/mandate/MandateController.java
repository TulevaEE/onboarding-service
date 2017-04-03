package ee.tuleva.onboarding.mandate;

import com.codeborne.security.mobileid.IdCardSignatureSession;
import com.codeborne.security.mobileid.MobileIDSession;
import com.codeborne.security.mobileid.MobileIdSignatureSession;
import com.codeborne.security.mobileid.SignatureFile;
import com.fasterxml.jackson.annotation.JsonView;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import ee.tuleva.onboarding.error.ValidationErrorsException;
import ee.tuleva.onboarding.mandate.command.CreateMandateCommand;
import ee.tuleva.onboarding.mandate.command.FinishIdCardSignCommand;
import ee.tuleva.onboarding.mandate.command.StartIdCardSignCommand;
import ee.tuleva.onboarding.mandate.exception.MandateNotFoundException;
import ee.tuleva.onboarding.mandate.response.IdCardSignatureResponse;
import ee.tuleva.onboarding.mandate.response.MandateSignatureStatusResponse;
import ee.tuleva.onboarding.mandate.response.MobileIdSignatureResponse;
import ee.tuleva.onboarding.user.User;
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

import static org.springframework.web.bind.annotation.RequestMethod.*;

@Slf4j
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class MandateController {

    private final MandateRepository mandateRepository;
    private final MandateService mandateService;
    private final GenericSessionStore genericSessionStore;
    private final SignatureFileArchiver signatureFileArchiver;
    private final MandateFileService mandateFileService;

    @ApiOperation(value = "Create a mandate")
    @RequestMapping(method = POST, value = "/mandates")
    @JsonView(MandateView.Default.class)
    public Mandate create(@ApiIgnore @AuthenticationPrincipal User user,
                                 @Valid @RequestBody CreateMandateCommand createMandateCommand,
                                 @ApiIgnore @Valid Errors errors) throws ValidationErrorsException {
        if (errors.hasErrors()) {
            log.info("Create mandate command is not valid: {}", errors);
            throw new ValidationErrorsException(errors);
        }

        return mandateService.save(user, createMandateCommand);
    }

    @ApiOperation(value = "Start signing mandate with mobile ID")
    @RequestMapping(method = PUT, value = "/mandates/{id}/signature/mobileId")
    public MobileIdSignatureResponse startMobileIdSignature(@PathVariable("id") Long mandateId,
                                                            @ApiIgnore @AuthenticationPrincipal User user) {

        Optional<MobileIDSession> session = genericSessionStore.get(MobileIDSession.class);
        MobileIDSession loginSession = session
                .orElseThrow(() -> new IllegalStateException("No mobile id session found"));

        MobileIdSignatureSession signatureSession = mandateService.mobileIdSign(mandateId, user, loginSession.phoneNumber);
        genericSessionStore.save(signatureSession);

        return new MobileIdSignatureResponse(signatureSession.challenge);
    }

    @ApiOperation(value = "Is mandate successfully signed with mobile ID")
    @RequestMapping(method = GET, value = "/mandates/{id}/signature/mobileId/status")
    public MandateSignatureStatusResponse getMobileIdSignatureStatus(@PathVariable("id") Long mandateId,
                                                                     @ApiIgnore @AuthenticationPrincipal User user,
                                                                     @RequestHeader("x-statistics-identifier") UUID statisticsIdentifier) {

        Optional<MobileIdSignatureSession> signatureSession = genericSessionStore.get(MobileIdSignatureSession.class);
        MobileIdSignatureSession session = signatureSession
                .orElseThrow(() -> new IllegalStateException("No mobile ID signature session found"));

        String statusCode = mandateService.finalizeMobileIdSignature(user, statisticsIdentifier, mandateId, session);

        return new MandateSignatureStatusResponse(statusCode);
    }

    @ApiOperation(value = "Start signing mandate with ID card")
    @RequestMapping(method = PUT, value = "/mandates/{id}/signature/idCard")
    public IdCardSignatureResponse startIdCardSign(@PathVariable("id") Long mandateId,
                                                   @ApiIgnore @AuthenticationPrincipal User user,
                                                   @Valid @RequestBody StartIdCardSignCommand signCommand) {

        IdCardSignatureSession signatureSession = mandateService.idCardSign(mandateId, user, signCommand.getClientCertificate());

        genericSessionStore.save(signatureSession);

        return new IdCardSignatureResponse(signatureSession.hash);
    }

    @ApiOperation(value = "Is mandate successfully signed with ID card")
    @RequestMapping(method = PUT, value = "/mandates/{id}/signature/idCard/status")
    public MandateSignatureStatusResponse getIdCardSignatureStatus(@PathVariable("id") Long mandateId,
                                                                   @Valid @RequestBody FinishIdCardSignCommand signCommand,
                                                                   @ApiIgnore @AuthenticationPrincipal User user,
                                                                   @RequestHeader(value = "x-statistics-identifier", required = false) UUID statisticsIdentifier) {

        Optional<IdCardSignatureSession> signatureSession = genericSessionStore.get(IdCardSignatureSession.class);
        IdCardSignatureSession session = signatureSession
                .orElseThrow(() -> new IllegalStateException("No ID card signature session found"));

        String statusCode = mandateService.finalizeIdCardSignature(user, statisticsIdentifier, mandateId, session, signCommand.getSignedHash());

        return new MandateSignatureStatusResponse(statusCode);
    }

    @ApiOperation(value = "Get mandate file")
    @RequestMapping(method = GET, value = "/mandates/{id}/file")
    public void getMandateFile(@PathVariable("id") Long mandateId,
                               @ApiIgnore @AuthenticationPrincipal User user,
                               HttpServletResponse response) throws IOException {

        Mandate mandate = getMandateOrThrow(mandateId, user);
        response.addHeader("Content-Disposition", "attachment; filename=Tuleva_avaldus.bdoc");

        byte[] content = mandate.getMandate().orElseThrow(() -> new RuntimeException("Mandate is not signed"));

        IOUtils.copy(new ByteArrayInputStream(content), response.getOutputStream());
        response.flushBuffer();

    }

    @ApiOperation(value = "Get mandate file")
    @RequestMapping(method = GET, value = "/mandates/{id}/file/preview", produces="application/zip")
    public void getMandateFilePreview(@PathVariable("id") Long mandateId,
                               @ApiIgnore @AuthenticationPrincipal User user,
                               HttpServletResponse response) throws IOException {

        List<SignatureFile> files = mandateFileService.getMandateFiles(mandateId, user);
        response.addHeader("Content-Disposition", "attachment; filename=Tuleva_avaldus.zip");

        signatureFileArchiver.writeSignatureFilesToZipOutputStream(files, response.getOutputStream());
        response.flushBuffer();

    }

    private Mandate getMandateOrThrow(Long mandateId, User user) {
        Mandate mandate = mandateRepository.findByIdAndUser(mandateId, user);

        if(mandate == null) {
            throw new MandateNotFoundException();
        }

        return mandate;
    }

}
