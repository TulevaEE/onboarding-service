package ee.tuleva.onboarding.auth;

import ee.tuleva.onboarding.auth.authority.GrantedAuthorityFactory;
import ee.tuleva.onboarding.auth.event.AfterTokenGrantedEvent;
import ee.tuleva.onboarding.auth.event.BeforeTokenGrantedEvent;
import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import ee.tuleva.onboarding.auth.jwt.TokenType;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import io.jsonwebtoken.ExpiredJwtException;
import java.util.List;
import javax.ws.rs.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
  private final List<AuthProvider> providers;
  private final ApplicationEventPublisher eventPublisher;
  private final JwtTokenUtil jwtTokenUtil;
  private final GrantedAuthorityFactory grantedAuthorityFactory;
  private final PrincipalService principalService;

  public AccessAndRefreshToken authenticate(GrantType grantType, String authenticationHash) {

    final var authenticatedPerson =
        providers.stream()
            .filter(it -> it.supports(grantType))
            .map(it -> it.authenticate(authenticationHash))
            .findFirst()
            .orElse(null);

    if (authenticatedPerson != null) {
      final var authorities = grantedAuthorityFactory.from(authenticatedPerson);

      eventPublisher.publishEvent(
          new BeforeTokenGrantedEvent(this, authenticatedPerson, grantType));

      String jwtToken = jwtTokenUtil.generateAccessToken(authenticatedPerson, authorities);
      String refreshToken = jwtTokenUtil.generateRefreshToken(authenticatedPerson, authorities);

      final var tokens = new AccessAndRefreshToken(jwtToken, refreshToken);
      eventPublisher.publishEvent(new AfterTokenGrantedEvent(this, authenticatedPerson, tokens));

      return tokens;
    }
    return null;
  }

  public AccessAndRefreshToken refreshToken(String refreshToken) {

    try {
      TokenType tokenType = jwtTokenUtil.getTypeFromToken(refreshToken);
      if (tokenType != TokenType.REFRESH) {
        throw new BadRequestException("Only refresh token is allowed for refresh request.");
      }

      AuthenticatedPerson authenticatedPerson =
          principalService.getFrom(
              jwtTokenUtil.getPersonFromToken(refreshToken),
              jwtTokenUtil.getAttributesFromToken(refreshToken));

      final var authorities = grantedAuthorityFactory.from(authenticatedPerson);
      String newAccessToken = jwtTokenUtil.generateAccessToken(authenticatedPerson, authorities);
      String newRefreshToken = jwtTokenUtil.generateRefreshToken(authenticatedPerson, authorities);

      return new AccessAndRefreshToken(newAccessToken, newRefreshToken);
    } catch (ExpiredJwtException e) {
      throw new ExpiredRefreshJwtException();
    }
  }
}
