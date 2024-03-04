package ee.tuleva.onboarding.auth;

import static ee.tuleva.onboarding.auth.command.AuthenticationType.SMART_ID;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

import com.fasterxml.jackson.annotation.JsonProperty;
import ee.tuleva.onboarding.auth.command.AuthenticateCommand;
import ee.tuleva.onboarding.auth.idcard.IdCardAuthService;
import ee.tuleva.onboarding.auth.mobileid.MobileIDSession;
import ee.tuleva.onboarding.auth.mobileid.MobileIdAuthService;
import ee.tuleva.onboarding.auth.response.AuthenticateResponse;
import ee.tuleva.onboarding.auth.response.IdCardLoginResponse;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import ee.tuleva.onboarding.auth.smartid.SmartIdAuthService;
import ee.tuleva.onboarding.auth.smartid.SmartIdSession;
import ee.tuleva.onboarding.error.ValidationErrorsException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import java.net.URLDecoder;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.*;

@RestController
@Slf4j
@RequiredArgsConstructor
public class AuthController {

  private final MobileIdAuthService mobileIdAuthService;
  private final SmartIdAuthService smartIdAuthService;
  private final IdCardAuthService idCardAuthService;
  private final GenericSessionStore genericSessionStore;
  private final AuthService authService;

  @Value("${frontend.url}")
  private String frontendUrl;

  @Operation(summary = "Initiate authentication")
  @RequestMapping(
      method = POST,
      value = "/authenticate",
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<AuthenticateResponse> authenticate(
      @Valid @RequestBody AuthenticateCommand authenticateCommand,
      @Parameter(hidden = true) Errors errors) {

    if (errors != null && errors.hasErrors()) {
      throw new ValidationErrorsException(errors);
    }

    if (SMART_ID == authenticateCommand.getType()) {
      SmartIdSession loginSession =
          smartIdAuthService.startLogin(authenticateCommand.getPersonalCode());
      return new ResponseEntity<>(
          AuthenticateResponse.fromSmartIdSession(loginSession), HttpStatus.OK);
    }

    MobileIDSession loginSession =
        mobileIdAuthService.startLogin(
            authenticateCommand.getPhoneNumber(), authenticateCommand.getPersonalCode());
    genericSessionStore.save(loginSession);
    return new ResponseEntity<>(
        AuthenticateResponse.fromMobileIdSession(loginSession), HttpStatus.OK);
  }

  @PostMapping({"/oauth/token", "/login", "/v1/tokens"})
  public AccessAndRefreshToken login(
      @RequestParam("grant_type") String grantType,
      @RequestParam(value = "authenticationHash", required = false) String authenticationHash) {
    return authService.authenticate(GrantType.valueOf(grantType.toUpperCase()), authenticationHash);
  }

  @PostMapping("/oauth/refresh-token")
  public AccessAndRefreshToken refreshAccessToken(
      @RequestBody RefreshTokenRequest refreshTokenRequest) {
    return authService.refreshToken(refreshTokenRequest.refreshToken);
  }

  public record RefreshTokenRequest(@JsonProperty("refresh_token") String refreshToken) {}

  @SneakyThrows
  @Operation(summary = "ID card login")
  @RequestMapping(
      method = {GET, POST},
      value = "/idLogin")
  @ResponseBody
  public IdCardLoginResponse idLogin(
      @RequestHeader(value = "ssl-client-verify") String clientCertificateVerification,
      @RequestHeader(value = "ssl-client-cert") String clientCertificate,
      @Parameter(hidden = true) HttpServletResponse response,
      @Parameter(hidden = true) HttpMethod httpMethod) {
    if (!"SUCCESS".equals(clientCertificateVerification)) {
      throw new IllegalStateException("Client certificate not verified");
    }

    idCardAuthService.checkCertificate(URLDecoder.decode(clientCertificate, UTF_8.name()));

    if (httpMethod.equals(HttpMethod.GET)) {
      response.sendRedirect(frontendUrl + "/?login=idCard");
    }
    return IdCardLoginResponse.success();
  }
}
