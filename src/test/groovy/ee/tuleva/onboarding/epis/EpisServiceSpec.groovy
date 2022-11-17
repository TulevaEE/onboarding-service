package ee.tuleva.onboarding.epis

import ee.tuleva.onboarding.epis.account.FundBalanceDto
import ee.tuleva.onboarding.epis.application.ApplicationResponse
import ee.tuleva.onboarding.epis.cashflows.CashFlowStatement
import ee.tuleva.onboarding.epis.contact.ContactDetails
import ee.tuleva.onboarding.epis.fund.FundDto
import ee.tuleva.onboarding.epis.fund.NavDto
import ee.tuleva.onboarding.epis.mandate.ApplicationDTO
import ee.tuleva.onboarding.epis.mandate.ApplicationResponseDTO
import ee.tuleva.onboarding.epis.mandate.MandateDto
import org.springframework.http.HttpEntity
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import spock.lang.Specification

import java.time.LocalDate

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.epis.cancellation.CancellationFixture.sampleCancellation
import static ee.tuleva.onboarding.epis.cashflows.CashFlowFixture.cashFlowFixture
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture
import static ee.tuleva.onboarding.epis.fund.FundDto.FundStatus.ACTIVE
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate
import static org.springframework.http.HttpStatus.OK

class EpisServiceSpec extends Specification {

  RestTemplate restTemplate = Mock(RestTemplate)
  RestTemplate clientCredentialsRestTemplate = Mock(RestTemplate)
  EpisService service = new EpisService(restTemplate, clientCredentialsRestTemplate)

  def setup() {
    service.episServiceUrl = "http://epis"
  }

  def "Send mandate: "() {
    given:
    def sampleMandate = sampleMandate()
    def mandateDto = MandateDto.builder()
        .id(sampleMandate.id)
        .build()

    1 * restTemplate.postForObject(_ as String, { HttpEntity httpEntity ->
      httpEntity.body.id == sampleMandate.id
    }, ApplicationResponseDTO.class)

    when:
    service.sendMandate(mandateDto)

    then:
    true
  }

  def "getApplications: "() {
    given:
    ApplicationDTO[] responseBody = [ApplicationDTO.builder().build()]
    ResponseEntity<ApplicationDTO[]> result =
        new ResponseEntity(responseBody, OK)

    1 * restTemplate.getForEntity(
        "http://epis/applications", ApplicationDTO[].class) >> result

    when:
    List<ApplicationDTO> transferApplicationDTOList =
        service.getApplications(samplePerson())

    then:
    transferApplicationDTOList.size() == 1
  }

  def "getContactDetails"() {
    given:
    def fixture = contactDetailsFixture()
    ResponseEntity<ContactDetails> response = new ResponseEntity(fixture, OK)

    1 * restTemplate.getForEntity(
        _ as String,
        ContactDetails.class
    ) >> response
    when:
    ContactDetails contactDetails = service.getContactDetails(samplePerson())

    then:
    contactDetails == fixture
  }

  def "getCashFlowStatement calls the right endpoint"() {
    given:
    CashFlowStatement cashFlowStatement = cashFlowFixture()
    ResponseEntity<CashFlowStatement> response = new ResponseEntity(cashFlowStatement, OK)

    LocalDate fromDate = LocalDate.parse("2001-01-01")
    LocalDate toDate = LocalDate.parse("2018-01-01")

    1 * restTemplate.getForEntity(
        "http://epis/account-cash-flow-statement?from-date=2001-01-01&to-date=2018-01-01", CashFlowStatement.class) >> response

    when:
    CashFlowStatement responseDto = service.getCashFlowStatement(samplePerson(), fromDate, toDate)

    then:
    cashFlowStatement == responseDto
  }

  def "gets account statement"() {
    given:
    FundBalanceDto fundBalanceDto = FundBalanceDto.builder().isin("someIsin").build()
    FundBalanceDto[] response = [fundBalanceDto]

    1 * restTemplate.getForEntity(
        _ as String, FundBalanceDto[].class) >> new ResponseEntity(response, OK)

    when:
    List<FundBalanceDto> fundBalances = service.getAccountStatement(samplePerson())

    then:
    fundBalances == response
  }

  def "gets funds"() {
    given:

    FundDto[] sampleFunds = [new FundDto("EE3600109435", "Tuleva Maailma Aktsiate Pensionifond", "TUK75", 2, ACTIVE)]

    1 * restTemplate.getForEntity(
        _ as String, FundDto[].class) >> new ResponseEntity(sampleFunds, OK)

    when:
    List<FundDto> funds = service.getFunds()

    then:
    funds == sampleFunds
  }

  def "Updates contact details"() {
    given:
    def contactDetails = contactDetailsFixture()

    1 * restTemplate.postForObject(_ as String, { HttpEntity httpEntity ->
      httpEntity.body.personalCode == contactDetails.personalCode
    }, ContactDetails.class)

    when:
    service.updateContactDetails(samplePerson, contactDetails)

    then:
    true
  }

  def "gets nav"() {
    given:
    def navDto = Mock(NavDto)
    1 * clientCredentialsRestTemplate.getForEntity("http://epis/navs/EE666?date=2018-10-20", NavDto.class) >>
        new ResponseEntity<NavDto>(navDto, OK)
    when:
    def result = service.getNav("EE666", LocalDate.parse("2018-10-20"))
    then:
    result == navDto
  }

  def "can send cancellations"() {
    given:
    def sampleCancellation = sampleCancellation()

    1 * restTemplate.postForObject(_ as String, { HttpEntity httpEntity ->
      httpEntity.body.id == sampleCancellation.id
    }, ApplicationResponse.class)

    when:
    service.sendCancellation(sampleCancellation)

    then:
    true
  }
}
