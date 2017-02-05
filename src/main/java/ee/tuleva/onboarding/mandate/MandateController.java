package ee.tuleva.onboarding.mandate;

import com.codeborne.security.mobileid.MobileIdSignatureSession;
import com.fasterxml.jackson.annotation.JsonView;
import ee.tuleva.onboarding.auth.MobileIdSessionStore;
import ee.tuleva.onboarding.auth.MobileIdSignatureSessionStore;
import ee.tuleva.onboarding.user.User;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.*;
import springfox.documentation.annotations.ApiIgnore;

import javax.validation.Valid;

import static org.springframework.web.bind.annotation.RequestMethod.*;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class MandateController {

    private final MandateService mandateService;
    private final MobileIdSignatureSessionStore mobileIdSessionStore;

    @ApiOperation(value = "Create a mandate")
    @RequestMapping(method = POST, value = "/mandate")
    @JsonView(MandateView.Default.class)
    public Mandate create(@ApiIgnore @AuthenticationPrincipal User user,
                          @Valid @RequestBody CreateMandateCommand createMandateCommand,
                          @ApiIgnore @Valid Errors errors) {

        if (errors.hasErrors()) {
            throw new ErrorsValidationException(errors);
        }

        return mandateService.save(user, createMandateCommand);
    }

    @ApiOperation(value = "Sign mandate")
    @RequestMapping(method = PUT, value = "/mandate/{id}/signature")
    public MandateSignatureResponse startSign(@PathVariable("id") Long mandateId,
                                              @ApiIgnore @AuthenticationPrincipal User user) {
        MobileIdSignatureSession session = mandateService.sign(mandateId, user);

        mobileIdSessionStore.save(new MandateSignatureSession(session.sessCode, session.challenge));

        return MandateSignatureResponse.builder()
                .mobileIdChallengeCode(session.challenge)
                .build();
    }

    @ApiOperation(value = "Is mandate successfully signed")
    @RequestMapping(method = GET, value = "/mandate/{id}/signature")
    public MandateSignatureStatusResponse getSignatureStatus(@PathVariable("id") String mandateId,
                                                             @ApiIgnore @AuthenticationPrincipal User user) {

        MandateSignatureSession session = mobileIdSessionStore.get();
        String status = mandateService.getSignatureStatus(session);

        return MandateSignatureStatusResponse.builder()
                .statusCode(status)
                .build();
    }


}
