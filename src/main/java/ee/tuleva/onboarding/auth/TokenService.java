package ee.tuleva.onboarding.auth;

import ee.tuleva.onboarding.auth.authority.GrantedAuthorityFactory;
import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenService {

  private final JwtTokenUtil jwtTokenUtil;
  private final GrantedAuthorityFactory grantedAuthorityFactory;

  public AuthenticationTokens generateTokens(AuthenticatedPerson person) {
    var authorities = grantedAuthorityFactory.from(person);
    return new AuthenticationTokens(
        jwtTokenUtil.generateAccessToken(person, authorities),
        jwtTokenUtil.generateRefreshToken(person, authorities));
  }

  public String generateAccessToken(AuthenticatedPerson person) {
    return jwtTokenUtil.generateAccessToken(person, grantedAuthorityFactory.from(person));
  }
}
