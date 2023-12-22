package ee.tuleva.onboarding.epis

import ee.tuleva.onboarding.contribution.Contribution
import ee.tuleva.onboarding.epis.application.ApplicationResponse
import ee.tuleva.onboarding.epis.mandate.ApplicationDTO
import ee.tuleva.onboarding.epis.mandate.ApplicationResponseDTO
import ee.tuleva.onboarding.epis.mandate.ApplicationStatus
import ee.tuleva.onboarding.epis.mandate.MandateDto
import org.mockserver.client.MockServerClient
import org.mockserver.matchers.MatchType
import org.mockserver.model.MediaType
import org.mockserver.springtest.MockServerTest
import org.mockserver.verify.VerificationTimes
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
import org.springframework.test.context.TestPropertySource
import spock.lang.Specification

import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.currency.Currency.EUR
import static ee.tuleva.onboarding.mandate.application.ApplicationType.TRANSFER
import static ee.tuleva.onboarding.user.address.AddressFixture.addressFixture
import static org.mockserver.model.HttpRequest.request
import static org.mockserver.model.HttpResponse.response
import static org.mockserver.model.JsonBody.json

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@MockServerTest("epis.service.url=http://localhost:\${mockServerPort}")
@TestPropertySource(properties = "spring.cache.type=ehcache")
@Import(Config.class)
class EpisServiceIntSpec extends Specification {


  static class Config {
    @Bean
    @ConditionalOnMissingBean(value = ErrorAttributes.class)
    DefaultErrorAttributes errorAttributes() {
      return new DefaultErrorAttributes()
    }
  }

  @Autowired
  private EpisService episService

  @Autowired
  private CacheManager cacheManager

  private MockServerClient mockServerClient

  def setup() {
    setUpSecurityContext()
  }

  private void setUpSecurityContext() {
    SecurityContext sc = SecurityContextHolder.createEmptyContext()
    TestingAuthenticationToken authentication = new TestingAuthenticationToken("test", "dummy")
    sc.authentication = authentication
    SecurityContextHolder.context = sc
  }

  def cleanup() {
    SecurityContextHolder.clearContext()
    cacheManager.cacheNames.stream().forEach(cache -> cacheManager.getCache(cache).clear())
  }

  def "getApplications"() {
    given:
    mockServerClient
        .when(request("/applications")
            .withHeader("Authorization", "Bearer dummy"))
        .respond(response()
            .withContentType(MediaType.APPLICATION_JSON)
            .withBody(json("""
                        [{
                         "currency": "EUR",
                         "amount": 100.0,
                         "status": "PENDING",
                         "type": "TRANSFER",
                         "id": 123,
                         "documentNumber": "123456"
                        }]
                        """, MatchType.STRICT)))
    when:
    List<ApplicationDTO> applications = episService.getApplications(samplePerson())
    then:
    applications.size() == 1
    applications.first().status == ApplicationStatus.PENDING
    applications.first().type == TRANSFER
    applications.first().id == 123
    applications.first().documentNumber == "123456"
  }

  def "getApplications - only one request per person is allowed to run at any time"() {
    given:
    mockServerClient
        .when(request("/applications")
            .withHeader("Authorization", "Bearer dummy"))
        .respond(response()
            .withDelay(TimeUnit.MILLISECONDS, 500)
            .withContentType(MediaType.APPLICATION_JSON)
            .withBody(json("""
                        [{
                         "currency": "EUR",
                         "amount": 100.0,
                         "status": "PENDING",
                         "type": "TRANSFER",
                         "id": 123,
                         "documentNumber": "123456"
                        }]
                        """, MatchType.STRICT)))
    when:
    ExecutorService executor = Executors.newFixedThreadPool(2)
    with(executor) {
      submit({
        setUpSecurityContext()
        episService.getApplications(samplePerson())
        SecurityContextHolder.clearContext()
      })
      submit({
        setUpSecurityContext()
        episService.getApplications(samplePerson())
        SecurityContextHolder.clearContext()
      })
      shutdown()
      awaitTermination(5, TimeUnit.SECONDS)
    }
    then:
    mockServerClient.verify(request().withPath("/applications"), VerificationTimes.exactly(2))
  }

  def "getCashFlowStatement - only one request per person is allowed to run at any time"() {
    given:
    mockServerClient
        .when(request("/account-cash-flow-statement")
            .withHeader("Authorization", "Bearer dummy"))
        .respond(response()
            .withDelay(TimeUnit.MILLISECONDS, 500)
            .withContentType(MediaType.APPLICATION_JSON)
            .withBody(json("""
                        {"startBalance": {}, "endBalance": {}, "transactions": []}
                        """, MatchType.STRICT)))
    when:
    ExecutorService executor = Executors.newFixedThreadPool(2)
    with(executor) {
      submit({
        setUpSecurityContext()
        episService.getCashFlowStatement(samplePerson(), LocalDate.EPOCH, LocalDate.EPOCH)
        SecurityContextHolder.clearContext()
      })
      submit({
        setUpSecurityContext()
        episService.getCashFlowStatement(samplePerson(), LocalDate.EPOCH, LocalDate.EPOCH)
        SecurityContextHolder.clearContext()
      })
      shutdown()
      awaitTermination(5, TimeUnit.SECONDS)
    }
    then:
    mockServerClient.verify(request().withPath("/account-cash-flow-statement"), VerificationTimes.exactly(1))
  }

  def "clear cache does not fail"() {
    when:
    episService.clearCache(samplePerson())
    then:
    noExceptionThrown()
  }

  def "can send mandates"() {
    given:
    def fundsTransferExchange =
        new MandateDto.MandateFundsTransferExchangeDTO("transferProcessId", 1.0, "EE123", "EE234", null)
    def address = addressFixture().build()
    def mandate = MandateDto.builder()
        .id(111L)
        .fundTransferExchanges([fundsTransferExchange])
        .processId("selectionProcessId")
        .createdDate(Instant.parse("2021-03-10T10:00:00Z"))
        .futureContributionFundIsin("EE345")
        .pillar(2)
        .address(address)
        .email("email@override.ee")
        .phoneNumber("+37288888888")
        .build()

    ApplicationResponseDTO expectedResponse = new ApplicationResponseDTO(
        ApplicationResponse.builder()
            .processId("responseProcessId")
            .applicationType(TRANSFER)
            .successful(true)
            .build()
    )

    mockServerClient
        .when(
            request()
                .withMethod("POST")
                .withPath("/mandates")
                .withBody(json("""
            {
              "id": ${mandate.id},
              "processId": "${mandate.processId}",
              "futureContributionFundIsin": "${mandate.futureContributionFundIsin}",
              "createdDate": "${mandate.createdDate}",
              "pillar": ${mandate.pillar},
              "fundTransferExchanges": [
                {
                  "processId": "${fundsTransferExchange.processId}",
                  "amount": ${fundsTransferExchange.amount},
                  "sourceFundIsin": "${fundsTransferExchange.sourceFundIsin}",
                  "targetFundIsin": "${fundsTransferExchange.targetFundIsin}",
                  "targetPik": ${fundsTransferExchange.targetPik}
                }
              ],
              "address": {
                "countryCode": "${address.countryCode}"
              },
              "email": "${mandate.email}",
              "phoneNumber": "${mandate.phoneNumber}",
              "paymentRate": ${mandate.paymentRate}
            }
          """, MatchType.STRICT))
        )
        .respond(
            response()
                .withContentType(MediaType.APPLICATION_JSON)
                .withBody(json(expectedResponse, MatchType.STRICT))
        )

    when:
    ApplicationResponseDTO response = episService.sendMandate(mandate)

    then:
    response == expectedResponse
  }

  def "can get contributions"() {
    given:
    def person = samplePerson()

    def expectedResponse = [new Contribution(
        Instant.parse("2023-04-26T10:00:00Z"),
        "Tuleva Fondid AS",
        12.34,
        EUR,
        2
    )]

    mockServerClient
        .when(
            request()
                .withMethod("GET")
                .withPath("/contributions")
        )
        .respond(
            response()
                .withContentType(MediaType.APPLICATION_JSON)
                .withBody(json(expectedResponse, MatchType.STRICT))
        )

    when:
    def response = episService.getContributions(person)

    then:
    response == expectedResponse
  }
}
