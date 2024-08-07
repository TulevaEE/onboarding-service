package ee.tuleva.onboarding.epis

import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil
import ee.tuleva.onboarding.epis.account.FundBalanceDto
import ee.tuleva.onboarding.epis.application.ApplicationResponse
import ee.tuleva.onboarding.epis.cashflows.CashFlowStatement
import ee.tuleva.onboarding.epis.contact.ContactDetails
import ee.tuleva.onboarding.epis.fund.FundDto
import ee.tuleva.onboarding.epis.fund.NavDto
import ee.tuleva.onboarding.epis.mandate.ApplicationDTO
import ee.tuleva.onboarding.epis.mandate.ApplicationResponseDTO
import ee.tuleva.onboarding.epis.mandate.MandateDto
import ee.tuleva.onboarding.epis.mandate.command.MandateCommand
import ee.tuleva.onboarding.epis.mandate.command.MandateCommandResponse
import ee.tuleva.onboarding.epis.payment.rate.PaymentRateDto
import org.springframework.http.HttpEntity
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.client.RestTemplate
import spock.lang.Specification

import java.time.Instant
import java.time.LocalDate

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.epis.MandateCommandResponseFixture.sampleMandateCommandResponse
import static ee.tuleva.onboarding.epis.cancellation.CancellationFixture.sampleCancellation
import static ee.tuleva.onboarding.epis.cashflows.CashFlowFixture.cashFlowFixture
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture
import static ee.tuleva.onboarding.epis.fund.FundDto.FundStatus.ACTIVE
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate
import static ee.tuleva.onboarding.mandate.application.ApplicationType.WITHDRAWAL
import static ee.tuleva.onboarding.user.address.AddressFixture.addressFixture
import static org.springframework.http.HttpMethod.GET
import static org.springframework.http.HttpStatus.OK

class EpisServiceSpec extends Specification {

  RestTemplate restTemplate = Mock(RestTemplate)
  JwtTokenUtil jwtTokenUtil = Mock(JwtTokenUtil)
  EpisService service = new EpisService(restTemplate, jwtTokenUtil)

  String sampleToken = "123"
  String sampleServiceToken = "123456"

  def setup() {
    service.episServiceUrl = "http://epis"

    jwtTokenUtil.generateServiceToken() >> sampleServiceToken

    Authentication sampleAuthentication = Mock(Authentication)
    sampleAuthentication.credentials >> sampleToken

    SecurityContextHolder.getContext().setAuthentication(sampleAuthentication)
  }

  def "Send mandate: "() {
    given:
    def sampleMandate = sampleMandate()
    def mandateDto = MandateDto.builder()
        .id(sampleMandate.id)
        .build()

    1 * restTemplate.postForObject(_ as String, {HttpEntity httpEntity ->
      doesHttpEntityContainToken(httpEntity, sampleToken) &&
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

    1 * restTemplate.exchange(
        "http://epis/applications", GET, {HttpEntity httpEntity ->
      doesHttpEntityContainToken(httpEntity, sampleToken)
    }, ApplicationDTO[].class) >> result

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

    1 * restTemplate.exchange(
        _ as String,
        GET,
        {HttpEntity httpEntity -> doesHttpEntityContainToken(httpEntity, sampleToken)
        },
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
        _ as String, GET, {HttpEntity httpEntity ->
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
        _ as String, GET, {HttpEntity httpEntity ->
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

    1 * restTemplate.postForObject(_ as String, {HttpEntity httpEntity ->
      doesHttpEntityContainToken(httpEntity, sampleToken) &&
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
    1 * restTemplate.exchange("http://epis/navs/EE666?date=2018-10-20", GET, _, NavDto.class) >>
        new ResponseEntity<NavDto>(navDto, OK)
    when:
    def result = service.getNav("EE666", LocalDate.parse("2018-10-20"))
    then:
    result == navDto
  }

  def "can send cancellations"() {
    given:
    def sampleCancellation = sampleCancellation()
    def mandateCommandResponse = sampleMandateCommandResponse("1", true, null, null)

    1 * restTemplate.postForObject(_ as String, {HttpEntity httpEntity ->
      doesHttpEntityContainToken(httpEntity, sampleToken) &&
          httpEntity.body.mandateDto.id == sampleCancellation.id
    }, MandateCommandResponse.class) >> mandateCommandResponse

    when:
    def response = service.sendMandateV2(new MandateCommand( "1", sampleCancellation))

    then:
    response.processId == "1"
    response.successful == true
  }

  def "can send payment rate application"() {
    given:
    def samplePaymentRate =PaymentRateDto.builder()
        .id(123L)
        .rate(BigDecimal.valueOf(4.0))
        .processId("rateProcessId")
        .createdDate(Instant.parse("2023-03-09T10:00:00Z"))
        .address(addressFixture().build())
        .email("email@override.ee")
        .phoneNumber("+37288888888")
        .build()

    1 * restTemplate.postForObject(_ as String, {HttpEntity httpEntity ->
      doesHttpEntityContainToken(httpEntity, sampleToken) &&
          httpEntity.body.id == samplePaymentRate.id
    }, ApplicationResponse.class)

    when:
    service.sendPaymentRateApplication(samplePaymentRate)

    then:
    true
  }

  boolean doesHttpEntityContainToken(HttpEntity httpEntity, String sampleToken) {
    httpEntity.headers.getFirst("authorization") == ("Bearer " + sampleToken)
  }
}
