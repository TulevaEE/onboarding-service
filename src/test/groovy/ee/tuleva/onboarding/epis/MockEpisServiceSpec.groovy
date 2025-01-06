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
import ee.tuleva.onboarding.epis.withdrawals.ArrestsBankruptciesDto
import ee.tuleva.onboarding.epis.withdrawals.FundPensionCalculationDto
import ee.tuleva.onboarding.withdrawals.FundPensionStatus
import org.springframework.web.client.RestTemplate
import spock.lang.Specification

import java.time.LocalDate

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson

class MockEpisServiceSpec extends Specification {

  EpisService episService = new MockEpisService(Mock(RestTemplate))

  def "getApplications has a mock response"() {
    when:
    List<ApplicationDTO> response = episService.getApplications(samplePerson())
    then:
    !response.isEmpty()
  }

  def "getCashFlowStatement has a mock response"() {
    given:
    LocalDate fromDate = LocalDate.parse("2000-01-01")
    LocalDate toDate = LocalDate.parse("2050-01-01")

    when:
    CashFlowStatement response = episService.getCashFlowStatement(samplePerson(), fromDate, toDate)
    then:
    !response.transactions.isEmpty()
  }

  def "getContactDetails has a mock response"() {
    when:
    ContactDetails response = episService.getContactDetails(samplePerson())
    then:
    response != null
  }

  def "getAccountStatement has a mock response"() {
    when:
    List<FundBalanceDto> response = episService.getAccountStatement(samplePerson())
    then:
    !response.isEmpty()
  }

  def "getFunds has a mock response"() {
    when:
    List<FundDto> response = episService.getFunds()
    then:
    !response.isEmpty()
  }

  def "getNav has a mock response"() {
    when:
    NavDto response = episService.getNav("example isin", LocalDate.parse("2000-01-01"))
    then:
    response != null
  }

  def "sendMandate has a mock response"() {
    when:
    ApplicationResponseDTO response = episService.sendMandate(MandateDto.builder().build())
    then:
    response != null
  }

  def "sendCancellation has a mock response"() {
    when:
    ApplicationResponse response = episService.sendCancellation(ApplicationResponse.builder().build())
    then:
    response != null
  }

  def "updateContactDetails has a mock response"() {
    when:
    ContactDetails response = episService.updateContactDetails(samplePerson(), ContactDetails.builder().build())
    then:
    response != null
  }

  def "getFundPensionCalculation has a mock response"() {
    when:
    FundPensionCalculationDto response = episService.getFundPensionCalculation(samplePerson())
    then:
    response != null
  }

  def "getArrestsBankruptciesPresent has a mock response"() {
    when:
    ArrestsBankruptciesDto response = episService.getArrestsBankruptciesPresent(samplePerson())
    then:
    response != null
  }

  def "getFundPensionStatus has a mock response"() {
    when:
    FundPensionStatus response = episService.getFundPensionStatus(samplePerson())
    then:
    response != null
  }
}
