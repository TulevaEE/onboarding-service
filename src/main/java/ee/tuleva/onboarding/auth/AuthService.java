package ee.tuleva.onboarding.auth;

import static ee.tuleva.onboarding.auth.jwt.TokenType.REFRESH;

import ee.tuleva.onboarding.auth.event.AfterTokenGrantedEvent;
import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import io.jsonwebtoken.ExpiredJwtException;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {
  private final List<AuthProvider> providers;
  private final ApplicationEventPublisher eventPublisher;
  private final JwtTokenUtil jwtTokenUtil;
  private final TokenService tokenService;
  private final PrincipalService principalService;
  private final Clock clock;

  public @Nullable AuthenticationTokens authenticate(
      GrantType grantType, @Nullable String authenticationHash) {

    final var authenticatedPerson =
        providers.stream()
            .filter(it -> it.supports(grantType))
            .map(it -> it.authenticate(authenticationHash))
            .filter(Objects::nonNull)
            .findFirst()
            .map(person -> person.withAuthTime(clock.instant()))
            .orElse(null);

    if (authenticatedPerson == null) {
      return null;
    }

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

      var person = jwtTokenUtil.getPersonFromToken(refreshToken);
      var role = jwtTokenUtil.getRoleFromToken(refreshToken);
      if (principalService.refusesSelfServiceTo(person, role)) {
        log.info("Ended a minor's session on refresh while minor self-service is disabled");
        throw new ExpiredRefreshJwtException();
      }

      var authenticatedPerson =
          principalService.getFrom(person, jwtTokenUtil.getAttributesFromToken(refreshToken), role);

      String newAccessToken = tokenService.generateAccessToken(authenticatedPerson);

      return new AuthenticationTokens(newAccessToken, refreshToken);
    } catch (ExpiredJwtException e) {
      throw new ExpiredRefreshJwtException();
    }
  }
}
