package ee.tuleva.onboarding.test

import ee.tuleva.onboarding.auth.OAuth2Fixture
import ee.tuleva.onboarding.auth.PersonalCodeAuthentication
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.context.support.WithSecurityContextFactory

class WithPersonalCodeSecurityContextFactory implements WithSecurityContextFactory<WithPersonalCodeUser> {
  @Override
  SecurityContext createSecurityContext(WithPersonalCodeUser annotation) {
    PersonalCodeAuthentication personalCodeAuthentication = OAuth2Fixture.aPersonalCodeAuthentication()
    personalCodeAuthentication.principal.personalCode = annotation.value()
    SecurityContext context = SecurityContextHolder.createEmptyContext()
    context.setAuthentication(new TestingAuthenticationToken(personalCodeAuthentication, null, "CLIENT"))
    return context
  }
}
