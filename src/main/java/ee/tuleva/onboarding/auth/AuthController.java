package ee.tuleva.onboarding.auth;

import com.codeborne.security.mobileid.MobileIDSession;
import ee.tuleva.onboarding.auth.command.AuthenticateCommand;
import ee.tuleva.onboarding.auth.command.PollCommand;
import ee.tuleva.onboarding.auth.response.AuthenticateResponse;
import ee.tuleva.onboarding.auth.response.PollResponse;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import springfox.documentation.annotations.ApiIgnore;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import static org.springframework.web.bind.annotation.RequestMethod.POST;

@RestController
@Slf4j
@CrossOrigin(origins = "*")
public class AuthController {

    MobileIdAuthService mobileIdAuthService;

    private String MOBILE_ID_SESSION_VARIABLE = "mobileIdSession";

    @Autowired
    AuthController(MobileIdAuthService mobileIdAuthService) {
        this.mobileIdAuthService = mobileIdAuthService;
    }

    @ApiOperation(value = "Initiate authentication")
    @RequestMapping(
            method = POST,
            value = "/authenticate",
            consumes = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public ResponseEntity<AuthenticateResponse> authenticate(@Valid @RequestBody AuthenticateCommand authenticateCommand, @ApiIgnore HttpServletRequest request) {
            MobileIDSession mobileIDSession = mobileIdAuthService.startLogin(authenticateCommand.getPhoneNumber());

            request.getSession().setAttribute(MOBILE_ID_SESSION_VARIABLE, mobileIDSession.toString());

        return new ResponseEntity<AuthenticateResponse>(AuthenticateResponse.fromMobileIDSession(mobileIDSession), HttpStatus.OK);
    }

    @ApiOperation(value = "Mobile ID polling endpoint")
    @RequestMapping(
            method = POST,
            value = "/authenticate/is-complete",
            consumes = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public ResponseEntity<PollResponse> authenticate(@Valid @RequestBody PollCommand pollCommand, @ApiIgnore HttpServletRequest request) {
        MobileIDSession mobileIDSession = MobileIDSession.fromString((String) request.getSession().getAttribute(MOBILE_ID_SESSION_VARIABLE));
        boolean isComplete = mobileIdAuthService.isLoginComplete(mobileIDSession);
        return new ResponseEntity<PollResponse>(new PollResponse(isComplete), HttpStatus.OK);
    }
}
