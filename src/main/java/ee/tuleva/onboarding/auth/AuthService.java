package ee.tuleva.onboarding.auth;

import static ee.tuleva.onboarding.auth.jwt.TokenType.REFRESH;

import ee.tuleva.onboarding.auth.event.AfterTokenGrantedEvent;
import ee.tuleva.onboarding.auth.event.BeforeTokenGrantedEvent;
import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import io.jsonwebtoken.ExpiredJwtException;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
  private final List<AuthProvider> providers;
  private final ApplicationEventPublisher eventPublisher;
  private final JwtTokenUtil jwtTokenUtil;
  private final TokenService tokenService;
  private final PrincipalService principalService;

  public AuthenticationTokens authenticate(GrantType grantType, String authenticationHash) {

    final var authenticatedPerson =
        providers.stream()
            .filter(it -> it.supports(grantType))
            .map(it -> it.authenticate(authenticationHash))
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);

    if (authenticatedPerson == null) {
      return null;
    }

    eventPublisher.publishEvent(new BeforeTokenGrantedEvent(this, authenticatedPerson, grantType));

    final var tokens = tokenService.generateTokens(authenticatedPerson);

    eventPublisher.publishEvent(
        new AfterTokenGrantedEvent(this, authenticatedPerson, grantType, tokens));

    return tokens;
  }

  public AuthenticationTokens refreshToken(String refreshToken) {

    try {
      var tokenType = jwtTokenUtil.getTypeFromToken(refreshToken);
      if (tokenType != REFRESH) {
        throw new IllegalArgumentException("Only refresh token is allowed for refresh request.");
      }

      var authenticatedPerson =
          principalService.getFrom(
              jwtTokenUtil.getPersonFromToken(refreshToken),
              jwtTokenUtil.getAttributesFromToken(refreshToken),
              jwtTokenUtil.getActingAsFromToken(refreshToken));

      String newAccessToken = tokenService.generateAccessToken(authenticatedPerson);

      return new AuthenticationTokens(newAccessToken, refreshToken);
    } catch (ExpiredJwtException e) {
      throw new ExpiredRefreshJwtException();
    }
  }
}
