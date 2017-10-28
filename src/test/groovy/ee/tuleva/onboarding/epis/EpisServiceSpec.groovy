package ee.tuleva.onboarding.epis

import ee.tuleva.onboarding.mandate.MandateApplicationType
import ee.tuleva.onboarding.mandate.content.MandateXmlMessage
import ee.tuleva.onboarding.epis.mandate.TransferExchangeDTO
import ee.tuleva.onboarding.epis.contact.UserPreferences
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.provider.authentication.OAuth2AuthenticationDetails
import org.springframework.web.client.RestTemplate
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static UserPreferences.defaultUserPreferences

class EpisServiceSpec extends Specification {

    RestTemplate restTemplate = Mock(RestTemplate)
    FundBalanceDTOToFundBalanceConverter fundBalanceConverter = Mock(FundBalanceDTOToFundBalanceConverter)

    EpisService service = new EpisService(restTemplate, fundBalanceConverter)

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
            doesHttpEntityContainToken(httpEntity, sampleToken) &&
            httpEntity.body.messages[0].processId == sampleMessages.get(0).processId
        }, CreateProcessingCommand.class) >> sampleCreateProcessingCommand

        when:
        service.process(sampleMessages)

        then:
        true

    }

    def "getFundTransferExchanges: "() {
        given:

        TransferExchangeDTO[] responseBody = [TransferExchangeDTO.builder().build()]
        ResponseEntity<TransferExchangeDTO[]> result =
                new ResponseEntity(responseBody, HttpStatus.OK)

        1 * restTemplate.exchange(
                _ as String, HttpMethod.GET, { HttpEntity httpEntity ->
            doesHttpEntityContainToken(httpEntity, sampleToken)
        }, TransferExchangeDTO[].class) >> result

        when:
        List<TransferExchangeDTO> transferApplicationDTOList =
                service.getTransferApplications(samplePerson())

        then:
        transferApplicationDTOList.size() == 1
    }

    def "getContactDetails"() {
        given:

        UserPreferences userPreferences = defaultUserPreferences()
        ResponseEntity<UserPreferences> response =
                new ResponseEntity(userPreferences, HttpStatus.OK)

        1 * restTemplate.exchange(
                _ as String, HttpMethod.GET, { HttpEntity httpEntity ->
            doesHttpEntityContainToken(httpEntity, sampleToken)
        }, UserPreferences.class) >> response

        when:
        UserPreferences contactDetails = service.getContactDetails(samplePerson())

        then:
        contactDetails == userPreferences
    }

    boolean doesHttpEntityContainToken(HttpEntity httpEntity, String sampleToken) {
        httpEntity.headers.getFirst("authorization") == ("Bearer " + sampleToken)
    }

    List<MandateXmlMessage> sampleMessages = [new MandateXmlMessage("123", "message", MandateApplicationType.SELECTION)]
}
