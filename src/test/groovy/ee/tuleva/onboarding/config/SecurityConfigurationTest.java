package ee.tuleva.onboarding.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ee.tuleva.onboarding.auth.jwt.JwtAuthorizationFilter;
import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import org.junit.jupiter.api.Test;

class SecurityConfigurationTest {

  private final SecurityConfiguration configuration = new SecurityConfiguration();

  @Test
  void jwtAuthorizationFilterIsBuiltFromTheProvidedCollaborators() {
    JwtTokenUtil jwtTokenUtil = mock(JwtTokenUtil.class);
    PrincipalService principalService = mock(PrincipalService.class);

    JwtAuthorizationFilter filter =
        configuration.jwtAuthorizationFilter(jwtTokenUtil, principalService);

    assertThat(filter).isNotNull();
  }
}
