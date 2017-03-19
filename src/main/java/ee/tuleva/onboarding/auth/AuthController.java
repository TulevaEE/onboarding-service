package ee.tuleva.onboarding.auth;

import com.codeborne.security.mobileid.MobileIDSession;
import ee.tuleva.onboarding.auth.command.AuthenticateCommand;
import ee.tuleva.onboarding.auth.idcard.IdCardAuthService;
import ee.tuleva.onboarding.auth.mobileid.MobileIdAuthService;
import ee.tuleva.onboarding.auth.response.AuthenticateResponse;
import ee.tuleva.onboarding.auth.response.IdCardLoginResponse;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.common.exceptions.UnauthorizedClientException;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Objects;

import static org.springframework.web.bind.annotation.RequestMethod.POST;

@RestController
@Slf4j
@RequiredArgsConstructor
public class AuthController {

    private final MobileIdAuthService mobileIdAuthService;
    private final GenericSessionStore genericSessionStore;
    private final IdCardAuthService idCardAuthService;

    @Value("${id-card.secret.token:Bearer ${random.uuid}}")
    private String idCardSecretToken;

    @ApiOperation(value = "Initiate authentication")
    @RequestMapping(
            method = POST,
            value = "/authenticate",
            consumes = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public ResponseEntity<AuthenticateResponse> authenticate(@Valid @RequestBody AuthenticateCommand authenticateCommand) {
        MobileIDSession loginSession = mobileIdAuthService.startLogin(authenticateCommand.getPhoneNumber());
        genericSessionStore.save(loginSession);
        return new ResponseEntity<>(AuthenticateResponse.fromMobileIdSession(loginSession), HttpStatus.OK);
    }

    @ApiOperation(value = "ID card login")
    @RequestMapping(method = POST, value = "/idLogin")
    @ResponseBody
    public IdCardLoginResponse idLogin(@RequestHeader(value = "ssl_client_verify") String clientCertificateVerification,
                                       @RequestHeader(value = "ssl_client_cert") String clientCertificate,
                                       @RequestHeader(value = "x-authorization") String crossAuthorizationToken) {
        if (!Objects.equals(crossAuthorizationToken, idCardSecretToken)) {
            throw new UnauthorizedClientException("Invalid X-Authorization");
        }
        if (!"SUCCESS".equals(clientCertificateVerification)) {
            throw new UnauthorizedClientException("Client certificate not verified");
        }
        idCardAuthService.checkCertificate(clientCertificate);

        return IdCardLoginResponse.success();
    }

}
