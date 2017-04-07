package ee.tuleva.onboarding.mandate.processor.implementation

import ee.tuleva.onboarding.mandate.MandateApplicationType
import ee.tuleva.onboarding.mandate.content.MandateXmlMessage
import org.springframework.http.HttpEntity
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.provider.authentication.OAuth2AuthenticationDetails
import org.springframework.web.client.RestTemplate
import spock.lang.Specification

class EpisServiceSpec extends Specification {

    RestTemplate restTemplate = Mock(RestTemplate)

    EpisService service = new EpisService(restTemplate)

    String sampleToken = "123"

    def setup() {

        OAuth2AuthenticationDetails sampleDetails = Mock(OAuth2AuthenticationDetails)
        sampleDetails.getTokenValue() >> sampleToken

        Authentication sampleAuthentication = Mock(Authentication)
        sampleAuthentication.getDetails() >> sampleDetails

        SecurityContextHolder.getContext().setAuthentication(sampleAuthentication);

    }

    def "Process: "() {
        given:
        CreateProcessingCommand sampleCreateProcessingCommand = new CreateProcessingCommand(sampleMessages)

        1 * restTemplate.postForObject(_ as String, {HttpEntity httpEntity ->
            httpEntity.headers.getFirst("authorization") == ("Bearer " + sampleToken) &&
            httpEntity.body.messages[0].processId == sampleMessages.get(0).processId
        }, CreateProcessingCommand.class) >> sampleCreateProcessingCommand

        when:
        service.process(sampleMessages)

        then:
        true

    }

    List<MandateXmlMessage> sampleMessages = [new MandateXmlMessage("123", "message", MandateApplicationType.SELECTION)]
}
