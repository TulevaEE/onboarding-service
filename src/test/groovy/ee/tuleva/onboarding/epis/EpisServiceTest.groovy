package ee.tuleva.onboarding.epis

import ee.tuleva.onboarding.auth.PersonFixture
import ee.tuleva.onboarding.epis.mandate.ApplicationDTO
import ee.tuleva.onboarding.epis.mandate.ApplicationStatus
import ee.tuleva.onboarding.mandate.application.ApplicationType
import org.mockserver.client.MockServerClient
import org.mockserver.model.MediaType
import org.mockserver.springtest.MockServerTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes
import org.springframework.boot.web.servlet.error.ErrorAttributes
import org.springframework.cache.CacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.provider.authentication.OAuth2AuthenticationDetails
import org.springframework.test.context.TestPropertySource
import spock.lang.Specification

import static org.mockserver.model.HttpRequest.request
import static org.mockserver.model.HttpResponse.response

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@MockServerTest("epis.service.url=http://localhost:\${mockServerPort}")
@TestPropertySource(properties = "spring.cache.type=ehcache")
@Import(Config.class)
class EpisServiceTest extends Specification {


    static class Config {
        @Bean
        @ConditionalOnMissingBean(value = ErrorAttributes.class)
        DefaultErrorAttributes errorAttributes() {
            return new DefaultErrorAttributes()
        }
    }

    @Autowired
    private EpisService episService

    private MockServerClient mockServerClient

    def setup() {
        SecurityContext sc = SecurityContextHolder.createEmptyContext()
        TestingAuthenticationToken authentication = new TestingAuthenticationToken("test", "password")
        OAuth2AuthenticationDetails details = Mock(OAuth2AuthenticationDetails)
        authentication.details = details
        details.getTokenValue() >> "dummy"
        sc.authentication = authentication
        SecurityContextHolder.context = sc
    }

    def cleanup() {
        SecurityContextHolder.clearContext()
    }

    def "getApplications"() {
        given:
        mockServerClient
            .when(request("/applications")
                .withHeader("Authorization", "Bearer dummy"))
            .respond(response()
                .withContentType(MediaType.APPLICATION_JSON)
                .withBody("""
                        [{
                         "currency": "EUR",
                         "amount": 100.0,
                         "status": "PENDING",
                         "type": "TRANSFER",
                         "id": 123,
                         "documentNumber": "123456"
                        }]
                        """))
        when:
        List<ApplicationDTO> applications = episService.getApplications(PersonFixture.samplePerson())
        then:
        applications.size() == 1
        applications.first().status == ApplicationStatus.PENDING
        applications.first().type == ApplicationType.TRANSFER
        applications.first().id == 123
        applications.first().documentNumber == "123456"
    }

    def "clear cache does not fail"() {
        when:
        episService.clearCache(PersonFixture.samplePerson())
        then:
        noExceptionThrown()
    }
}
