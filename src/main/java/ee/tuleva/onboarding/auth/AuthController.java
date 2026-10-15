package ee.tuleva.onboarding.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import ee.tuleva.onboarding.auth.command.AuthenticateCommand;
import ee.tuleva.onboarding.auth.command.IdCardAuthenticateCommand;
import ee.tuleva.onboarding.auth.command.MobileIdAuthenticateCommand;
import ee.tuleva.onboarding.auth.mobileid.MobileIdLoginStarter;
import ee.tuleva.onboarding.auth.response.AuthenticateResponse;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import ee.tuleva.onboarding.auth.webeid.WebEidAuthService;
import ee.tuleva.onboarding.error.ValidationErrorsException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.*;

@RestController
@Slf4j
@RequiredArgsConstructor
public class AuthController {

  private final MobileIdLoginStarter mobileIdLoginStarter;
  private final WebEidAuthService webEidAuthService;
  private final GenericSessionStore genericSessionStore;
  private final AuthService authService;

  @Operation(summary = "Initiate authentication")
  @PostMapping(value = "/authenticate", consumes = MediaType.APPLICATION_JSON_VALUE)
  public AuthenticateResponse authenticate(
      @Valid @RequestBody AuthenticateCommand command,
      @Parameter(hidden = true) Errors errors,
      @Parameter(hidden = true) HttpServletRequest request) {
    if (errors != null && errors.hasErrors()) {
      throw new ValidationErrorsException(errors);
    }
    return switch (command) {
      case IdCardAuthenticateCommand _ -> {
        var challengeNonce = webEidAuthService.generateChallenge();
        yield AuthenticateResponse.fromWebEidChallenge(challengeNonce);
      }
      case MobileIdAuthenticateCommand cmd -> {
        var loginSession =
            mobileIdLoginStarter.start(cmd.phoneNumber(), cmd.personalCode(), cmd.rememberMe());
        genericSessionStore.save(loginSession);
        yield AuthenticateResponse.fromMobileIdSession(loginSession);
      }
    };
  }

  @PostMapping({"/oauth/token", "/login", "/v1/tokens"})
  public @Nullable AuthenticationTokens login(
      @RequestParam("grant_type") String grantType,
      @RequestParam(required = false) @Nullable String authenticationHash) {
    return authService.authenticate(GrantType.valueOf(grantType.toUpperCase()), authenticationHash);
  }

  @PostMapping("/oauth/refresh-token")
  public AuthenticationTokens refreshAccessToken(
      @RequestBody RefreshTokenRequest refreshTokenRequest) {
    return authService.refreshToken(refreshTokenRequest.refreshToken);
  }

  public record RefreshTokenRequest(@JsonProperty("refresh_token") String refreshToken) {}
}
