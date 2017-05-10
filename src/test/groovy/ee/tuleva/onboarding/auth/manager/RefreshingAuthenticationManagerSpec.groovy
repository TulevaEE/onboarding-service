package ee.tuleva.onboarding.auth.manager

import ee.tuleva.onboarding.auth.PersonalCodeAuthentication
import ee.tuleva.onboarding.auth.authority.GrantedAuthorityFactory
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonNonMember
import static ee.tuleva.onboarding.auth.mobileid.MobileIdFixture.sampleMobileIdSession
import static java.util.Collections.emptyList
import static java.util.Collections.singletonList

class RefreshingAuthenticationManagerSpec extends Specification {

	def grantedAuthorityFactory = Mock(GrantedAuthorityFactory)
	def authManager = new RefreshingAuthenticationManager(grantedAuthorityFactory)

	def "authentication manager refreshes user authorities"() {
		given:
		def authenticatedPerson = sampleAuthenticatedPersonNonMember().build()
		def credentials = sampleMobileIdSession
		def authorities = emptyList()
		def userAuth = new PersonalCodeAuthentication(authenticatedPerson, credentials, authorities)
		def authentication = new PreAuthenticatedAuthenticationToken(userAuth, "", authorities)

		and:
		def newAuthorities = singletonList(new SimpleGrantedAuthority("MEMBER"))
		grantedAuthorityFactory.from(authenticatedPerson) >> newAuthorities

		when:
		def newUserAuth = authManager.authenticate(authentication)

		then:
		newUserAuth instanceof PersonalCodeAuthentication
		newUserAuth.principal == authenticatedPerson
		newUserAuth.credentials == credentials
		newUserAuth.authorities == newAuthorities
		newUserAuth.authenticated
	}
}
