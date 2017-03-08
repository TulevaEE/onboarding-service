package ee.tuleva.onboarding.auth;

import com.codeborne.security.mobileid.MobileIDSession;
import ee.tuleva.onboarding.auth.command.AuthenticateCommand;
import ee.tuleva.onboarding.auth.response.AuthenticateResponse;
import io.swagger.annotations.ApiOperation;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

import static org.springframework.web.bind.annotation.RequestMethod.POST;

@RestController
@Slf4j
@RequiredArgsConstructor
public class AuthController {

    private final MobileIdAuthService mobileIdAuthService;
    private final MobileIdSessionStore mobileIdSessionStore;

    @ApiOperation(value = "Initiate authentication")
    @RequestMapping(
            method = POST,
            value = "/authenticate",
            consumes = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public ResponseEntity<AuthenticateResponse> authenticate(@Valid @RequestBody AuthenticateCommand authenticateCommand) {
        MobileIDSession mobileIDSession = mobileIdAuthService.startLogin(authenticateCommand.getPhoneNumber());
        mobileIdSessionStore.save(mobileIDSession);
        return new ResponseEntity<>(AuthenticateResponse.fromMobileIDSession(mobileIDSession), HttpStatus.OK);
    }

    @ApiOperation(value = "ID card login")
    @RequestMapping(method = POST, value = "/idlogin")
    @ResponseBody
    public IdLoginResponse idLogin(@RequestHeader HttpHeaders headers,
                          @RequestHeader(value="ssl_client_verify") String clientCertificateVerification,
                          @RequestHeader(value="ssl_client_cert") String clientCertificate) {
        if(!"SUCCESS".equals(clientCertificateVerification)) {
            // do nothing
        }
        return IdLoginResponse.builder()
                .clientCertificateVerification(clientCertificateVerification)
                .clientCertificate(clientCertificate)
                .build();
    }

    @Data
    @Builder
    private class IdLoginResponse {
        private String clientCertificateVerification;
        private String clientCertificate;
    }

}
