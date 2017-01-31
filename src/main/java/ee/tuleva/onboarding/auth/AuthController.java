package ee.tuleva.onboarding.auth;

import com.codeborne.security.mobileid.MobileIDSession;
import ee.tuleva.onboarding.auth.command.AuthenticateCommand;
import ee.tuleva.onboarding.auth.response.AuthenticateResponse;
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

import javax.validation.Valid;

import static org.springframework.web.bind.annotation.RequestMethod.POST;

@RestController
@Slf4j
@CrossOrigin(origins = "*")
public class AuthController {

    private MobileIdAuthService mobileIdAuthService;

    @Autowired
    AuthController(MobileIdAuthService mobileIdAuthService) {
        this.mobileIdAuthService = mobileIdAuthService;
    }

    @ApiOperation(value = "Initiate authentication")
    @RequestMapping(
            method = POST,
            value = "/authenticate",
            consumes = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public ResponseEntity<AuthenticateResponse> authenticate(@Valid @RequestBody AuthenticateCommand authenticateCommand) {
            MobileIDSession mobileIDSession = mobileIdAuthService.startLogin(authenticateCommand.getPhoneNumber());
            MobileIdSessionStore.save(mobileIDSession);
        return new ResponseEntity<AuthenticateResponse>(AuthenticateResponse.fromMobileIDSession(mobileIDSession), HttpStatus.OK);
    }

}
