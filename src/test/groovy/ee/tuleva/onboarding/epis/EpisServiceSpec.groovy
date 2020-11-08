package ee.tuleva.onboarding.epis

import ee.tuleva.onboarding.epis.account.FundBalanceDto
import ee.tuleva.onboarding.epis.cashflows.CashFlowStatement
import ee.tuleva.onboarding.epis.contact.UserPreferences
import ee.tuleva.onboarding.epis.fund.FundDto
import ee.tuleva.onboarding.epis.fund.NavDto
import ee.tuleva.onboarding.epis.mandate.MandateDto
import ee.tuleva.onboarding.epis.mandate.MandateResponseDTO
import ee.tuleva.onboarding.epis.mandate.TransferExchangeDTO
import org.springframework.http.HttpEntity
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.OAuth2RestOperations
import org.springframework.security.oauth2.provider.authentication.OAuth2AuthenticationDetails
import org.springframework.web.client.RestTemplate
import spock.lang.Specification

import java.time.LocalDate

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.epis.cashflows.CashFlowFixture.cashFlowFixture
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture
import static ee.tuleva.onboarding.epis.fund.FundDto.FundStatus.ACTIVE
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate
import static org.springframework.http.HttpMethod.GET
import static org.springframework.http.HttpStatus.OK

class EpisServiceSpec extends Specification {

    RestTemplate restTemplate = Mock(RestTemplate)
    OAuth2RestOperations clientCredentialsRestTemplate = Mock(OAuth2RestOperations)
    EpisService service = new EpisService(restTemplate, clientCredentialsRestTemplate)

    String sampleToken = "123"

    def setup() {
        service.episServiceUrl = "http://epis"

        OAuth2AuthenticationDetails sampleDetails = Mock(OAuth2AuthenticationDetails)
        sampleDetails.getTokenValue() >> sampleToken

        Authentication sampleAuthentication = Mock(Authentication)
        sampleAuthentication.getDetails() >> sampleDetails

        SecurityContextHolder.getContext().setAuthentication(sampleAuthentication)
    }

    def "Send mandate: "() {
        given:
        def sampleMandate = sampleMandate()
        def mandateDto = MandateDto.builder()
            .id(sampleMandate.id)
            .build()

        1 * restTemplate.postForObject(_ as String, { HttpEntity httpEntity ->
            doesHttpEntityContainToken(httpEntity, sampleToken) &&
                httpEntity.body.id == sampleMandate.id
        }, MandateResponseDTO.class)

        when:
        service.sendMandate(mandateDto)

        then:
        true
    }

    def "getFundTransferExchanges: "() {
        given:
        TransferExchangeDTO[] responseBody = [TransferExchangeDTO.builder().build()]
        ResponseEntity<TransferExchangeDTO[]> result =
            new ResponseEntity(responseBody, OK)

        1 * restTemplate.exchange(
            _ as String, GET, { HttpEntity httpEntity ->
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

        UserPreferences userPreferences = contactDetailsFixture()
        ResponseEntity<UserPreferences> response =
            new ResponseEntity(userPreferences, OK)

        1 * restTemplate.exchange(
            _ as String, GET, { HttpEntity httpEntity ->
            doesHttpEntityContainToken(httpEntity, sampleToken)
        }, UserPreferences.class) >> response
        when:
        UserPreferences contactDetails = service.getContactDetails(samplePerson())

        then:
        contactDetails == userPreferences
    }

    def "getCashFlowStatement calls the right endpoint"() {
        given:
        CashFlowStatement cashFlowStatement = cashFlowFixture()
        ResponseEntity<CashFlowStatement> response = new ResponseEntity(cashFlowStatement, OK)

        LocalDate fromDate = LocalDate.parse("2001-01-01")
        LocalDate toDate = LocalDate.parse("2018-01-01")

        1 * restTemplate.exchange(
            "http://epis/account-cash-flow-statement?from-date=2001-01-01&to-date=2018-01-01", GET, {
            HttpEntity httpEntity -> doesHttpEntityContainToken(httpEntity, sampleToken)
        }, CashFlowStatement.class) >> response

        when:
        CashFlowStatement responseDto = service.getCashFlowStatement(samplePerson(), fromDate, toDate)

        then:
        cashFlowStatement == responseDto
    }

    def "gets account statement"() {
        given:
        FundBalanceDto fundBalanceDto = FundBalanceDto.builder().isin("someIsin").build()
        FundBalanceDto[] response = [fundBalanceDto]

        1 * restTemplate.exchange(
            _ as String, GET, { HttpEntity httpEntity ->
            doesHttpEntityContainToken(httpEntity, sampleToken)
        }, FundBalanceDto[].class) >> new ResponseEntity(response, OK)

        when:
        List<FundBalanceDto> fundBalances = service.getAccountStatement(samplePerson())

        then:
        fundBalances == response
    }

    def "gets funds"() {
        given:

        FundDto[] sampleFunds = [new FundDto("EE3600109435", "Tuleva Maailma Aktsiate Pensionifond", "TUK75", 2, ACTIVE)]

        1 * restTemplate.exchange(
            _ as String, GET, { HttpEntity httpEntity ->
            doesHttpEntityContainToken(httpEntity, sampleToken)
        }, FundDto[].class) >> new ResponseEntity(sampleFunds, OK)

        when:
        List<FundDto> funds = service.getFunds()

        then:
        funds == sampleFunds
    }

    def "Updates contact details"() {
        given:
        def contactDetails = contactDetailsFixture()

        1 * restTemplate.postForObject(_ as String, { HttpEntity httpEntity ->
            doesHttpEntityContainToken(httpEntity, sampleToken) &&
                httpEntity.body.personalCode == contactDetails.personalCode
        }, UserPreferences.class)

        when:
        service.updateContactDetails(samplePerson, contactDetails)

        then:
        true
    }

    def "gets nav"() {
        given:
        def navDto = Mock(NavDto)
        1 * clientCredentialsRestTemplate.exchange("http://epis/navs/EE666?date=2018-10-20", GET, _, NavDto.class) >>
            new ResponseEntity<NavDto>(navDto, OK);
        when:
        def result = service.getNav("EE666", LocalDate.parse("2018-10-20"))
        then:
        result == navDto
    }

    boolean doesHttpEntityContainToken(HttpEntity httpEntity, String sampleToken) {
        httpEntity.headers.getFirst("authorization") == ("Bearer " + sampleToken)
    }
}
