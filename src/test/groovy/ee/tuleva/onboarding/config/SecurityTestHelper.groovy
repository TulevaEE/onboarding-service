package ee.tuleva.onboarding.config

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonNonMember
import static ee.tuleva.onboarding.auth.authority.Authority.USER

class SecurityTestHelper {

  static Authentication mockAuthentication() {
    new UsernamePasswordAuthenticationToken(
        sampleAuthenticatedPersonNonMember().build(), null, [new SimpleGrantedAuthority(USER)])
  }
}
