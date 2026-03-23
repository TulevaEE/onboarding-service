package ee.tuleva.onboarding.auth;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.auth.authority.GrantedAuthorityFactory;
import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

  @Mock private JwtTokenUtil jwtTokenUtil;
  @Mock private GrantedAuthorityFactory grantedAuthorityFactory;
  @InjectMocks private TokenService tokenService;

  @Test
  void generatesAccessAndRefreshTokens() {
    var person = sampleAuthenticatedPersonAndMember().build();
    var authorities = List.of(new SimpleGrantedAuthority("USER"));
    doReturn(authorities).when(grantedAuthorityFactory).from(person);
    when(jwtTokenUtil.generateAccessToken(eq(person), eq(authorities))).thenReturn("access");
    when(jwtTokenUtil.generateRefreshToken(eq(person), eq(authorities))).thenReturn("refresh");

    AuthenticationTokens tokens = tokenService.generateTokens(person);

    assertThat(tokens.accessToken()).isEqualTo("access");
    assertThat(tokens.refreshToken()).isEqualTo("refresh");
  }

  @Test
  void generatesOnlyAccessToken() {
    var person = sampleAuthenticatedPersonAndMember().build();
    var authorities = List.of(new SimpleGrantedAuthority("USER"));
    doReturn(authorities).when(grantedAuthorityFactory).from(person);
    when(jwtTokenUtil.generateAccessToken(eq(person), eq(authorities))).thenReturn("access-only");

    String accessToken = tokenService.generateAccessToken(person);

    assertThat(accessToken).isEqualTo("access-only");
  }
}
