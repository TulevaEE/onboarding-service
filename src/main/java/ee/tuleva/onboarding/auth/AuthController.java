package ee.tuleva.onboarding.auth;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

import com.fasterxml.jackson.annotation.JsonProperty;
import ee.tuleva.onboarding.auth.command.AuthenticateCommand;
import ee.tuleva.onboarding.auth.command.IdCardAuthenticateCommand;
import ee.tuleva.onboarding.auth.command.MobileIdAuthenticateCommand;
import ee.tuleva.onboarding.auth.command.SmartIdAuthenticateCommand;
import ee.tuleva.onboarding.auth.idcard.IdCardAuthService;
import ee.tuleva.onboarding.auth.mobileid.MobileIdAuthService;
import ee.tuleva.onboarding.auth.response.AuthenticateResponse;
import ee.tuleva.onboarding.auth.response.IdCardLoginResponse;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import ee.tuleva.onboarding.auth.smartid.SmartIdAuthService;
import ee.tuleva.onboarding.auth.webeid.WebEidAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.net.URLDecoder;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@Slf4j
@RequiredArgsConstructor
public class AuthController {

  private final MobileIdAuthService mobileIdAuthService;
  private final SmartIdAuthService smartIdAuthService;
  private final IdCardAuthService idCardAuthService;
  private final WebEidAuthService webEidAuthService;
  private final GenericSessionStore genericSessionStore;
  private final AuthService authService;

  @Value("${frontend.url}")
  private String frontendUrl;

  @Operation(summary = "Initiate authentication")
  @PostMapping(value = "/authenticate", consumes = MediaType.APPLICATION_JSON_VALUE)
  public AuthenticateResponse authenticate(@Valid @RequestBody AuthenticateCommand command) {
    return switch (command) {
      case IdCardAuthenticateCommand _ -> {
        var challengeNonce = webEidAuthService.generateChallenge();
        yield AuthenticateResponse.fromWebEidChallenge(challengeNonce);
      }
      case SmartIdAuthenticateCommand cmd -> {
        var loginSession = smartIdAuthService.startLogin(cmd.personalCode());
        yield AuthenticateResponse.fromSmartIdSession(loginSession);
      }
      case MobileIdAuthenticateCommand cmd -> {
        var loginSession = mobileIdAuthService.startLogin(cmd.phoneNumber(), cmd.personalCode());
        genericSessionStore.save(loginSession);
        yield AuthenticateResponse.fromMobileIdSession(loginSession);
      }
    };
  }

  @PostMapping({"/oauth/token", "/login", "/v1/tokens"})
  public AuthenticationTokens login(
      @RequestParam("grant_type") String grantType,
      @RequestParam(required = false) String authenticationHash) {
    return authService.authenticate(GrantType.valueOf(grantType.toUpperCase()), authenticationHash);
  }

  @PostMapping("/oauth/refresh-token")
  public AuthenticationTokens refreshAccessToken(
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

    // Check if certificate still contains percent-encoded characters
    // NGINX sends: "-----BEGIN+CERTIFICATE-----%0A..." (contains %0A, %20, etc.)
    // ALB (after AlbMtlsHeaderFilter): "-----BEGIN CERTIFICATE-----\n..." (already decoded)
    String decodedCertificate;
    if (clientCertificate.contains("%0A") || clientCertificate.contains("%20")) {
      // NGINX path: Certificate is URL-encoded, needs URLDecoder
      decodedCertificate = URLDecoder.decode(clientCertificate, UTF_8.name());
    } else {
      // ALB path: Certificate already decoded by AlbMtlsHeaderFilter
      decodedCertificate = clientCertificate;
    }

    idCardAuthService.checkCertificate(decodedCertificate);

    if (httpMethod.equals(HttpMethod.GET)) {
      response.sendRedirect(frontendUrl + "/?login=idCard");
    }
    return IdCardLoginResponse.success();
  }
}
