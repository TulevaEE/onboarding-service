package ee.tuleva.onboarding.epis

import ee.tuleva.onboarding.epis.account.FundBalanceDto
import ee.tuleva.onboarding.epis.cashflows.CashFlowStatementDto
import ee.tuleva.onboarding.epis.cashflows.CashFlowValueDto
import ee.tuleva.onboarding.epis.contact.UserPreferences
import ee.tuleva.onboarding.epis.fund.FundDto
import ee.tuleva.onboarding.epis.mandate.TransferExchangeDTO
import ee.tuleva.onboarding.mandate.MandateApplicationType
import ee.tuleva.onboarding.mandate.content.MandateXmlMessage
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.provider.authentication.OAuth2AuthenticationDetails
import org.springframework.web.client.RestTemplate
import spock.lang.Specification

import java.text.SimpleDateFormat
import java.time.Instant

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.epis.contact.UserPreferences.defaultUserPreferences
import static ee.tuleva.onboarding.epis.fund.FundDto.FundStatus.ACTIVE

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

    def "getCashFlowStatement calls the right endpoint"() {

        given:
            service.episServiceUrl = "http://example.com"
            CashFlowStatementDto cashFlowStatementDto = getFakeCashFlowStatement()
            ResponseEntity<CashFlowStatementDto> response =
                    new ResponseEntity(cashFlowStatementDto, HttpStatus.OK)

            Instant startTime = parseInstant("2001-01-01")
            Instant endTime = parseInstant("2018-01-01")

            1 * restTemplate.exchange(
                    "http://example.com/account-cash-flow-statement?from-date=2001-01-01&to-date=2018-01-01", HttpMethod.GET, { HttpEntity httpEntity ->
                doesHttpEntityContainToken(httpEntity, sampleToken)
            }, CashFlowStatementDto.class) >> response

        when:
        CashFlowStatementDto responseDto = service.getCashFlowStatement(samplePerson(), startTime, endTime)

        then:
            cashFlowStatementDto == responseDto
    }

    def "gets account statement"() {
        given:
        FundBalanceDto fundBalanceDto = FundBalanceDto.builder().isin("someIsin").build()
        FundBalanceDto[] response = [fundBalanceDto]

        1 * restTemplate.exchange(
                _ as String, HttpMethod.GET, { HttpEntity httpEntity ->
            doesHttpEntityContainToken(httpEntity, sampleToken)
        }, FundBalanceDto[].class) >> new ResponseEntity(response, HttpStatus.OK)

        when:
        List<FundBalanceDto> fundBalances = service.getAccountStatement(samplePerson())

        then:
        fundBalances == response
    }

    def "gets funds"() {
        given:

        FundDto[] sampleFunds = [new FundDto("EE3600109435", "Tuleva Maailma Aktsiate Pensionifond", "TUK75", 2, ACTIVE)]

        1 * restTemplate.exchange(
                _ as String, HttpMethod.GET, { HttpEntity httpEntity ->
            doesHttpEntityContainToken(httpEntity, sampleToken)
        }, FundDto[].class) >> new ResponseEntity(sampleFunds, HttpStatus.OK)

        when:
        List<FundDto> funds = service.getFunds()

        then:
        funds == sampleFunds
    }

    private static CashFlowStatementDto getFakeCashFlowStatement() {
        Instant randomTime = parseInstant("2001-01-01")
        CashFlowStatementDto cashFlowStatementDto = CashFlowStatementDto.builder()
            .startBalance([
                    "1": new CashFlowValueDto(randomTime, 100, "EEK"),
                    "2": new CashFlowValueDto(randomTime, 115, "EUR"),
            ])
            .endBalance([
                    "1": new CashFlowValueDto(randomTime, 110, "EEK"),
                    "2": new CashFlowValueDto(randomTime, 125, "EUR"),
            ])
            .transactions([
                new CashFlowValueDto(randomTime, 100, "EEK"),
                new CashFlowValueDto(randomTime, 115, "EUR"),
            ]).build()
        return cashFlowStatementDto
    }

    boolean doesHttpEntityContainToken(HttpEntity httpEntity, String sampleToken) {
        httpEntity.headers.getFirst("authorization") == ("Bearer " + sampleToken)
    }

    List<MandateXmlMessage> sampleMessages = [new MandateXmlMessage("123", "message", MandateApplicationType.SELECTION)]

    private static Instant parseInstant(String format) {
        return new SimpleDateFormat("yyyy-MM-dd").parse(format).toInstant()
    }
}
