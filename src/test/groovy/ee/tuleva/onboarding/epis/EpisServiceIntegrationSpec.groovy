package ee.tuleva.onboarding.epis

import ee.tuleva.onboarding.applicationtype.ApplicationType
import ee.tuleva.onboarding.auth.role.Role
import ee.tuleva.onboarding.epis.ThirdPillarContribution
import ee.tuleva.onboarding.epis.application.ApplicationResponse
import ee.tuleva.onboarding.epis.mandate.ApplicationResponseDTO
import ee.tuleva.onboarding.epis.mandate.command.MandateCommandResponse
import ee.tuleva.onboarding.mandate.LegacyMandateSubmission
import ee.tuleva.onboarding.mandate.MandateProcessResult
import ee.tuleva.onboarding.mandate.MandateSubmissionCommand
import ee.tuleva.onboarding.mandate.application.ApplicationSnapshot
import ee.tuleva.onboarding.mandate.application.ApplicationStatus
import org.mockserver.client.MockServerClient
import org.mockserver.matchers.MatchType
import org.mockserver.model.MediaType
import org.mockserver.springtest.MockServerTest
import org.mockserver.verify.VerificationTimes
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cache.CacheManager
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

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember
import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonLegalEntity
import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.auth.role.RoleType.PERSON
import static ee.tuleva.onboarding.currency.Currency.EUR
import static ee.tuleva.onboarding.epis.MandateCommandResponseFixture.sampleMandateCommandResponse
import static ee.tuleva.onboarding.epis.cancellation.CancellationFixture.*
import static ee.tuleva.onboarding.applicationtype.ApplicationType.TRANSFER
import static ee.tuleva.onboarding.pillar.Pillar.SECOND
import static ee.tuleva.onboarding.country.CountryFixture.countryFixture
import static java.util.concurrent.TimeUnit.MILLISECONDS
import static java.util.concurrent.TimeUnit.SECONDS
import static org.mockserver.model.HttpRequest.request
import static org.mockserver.model.HttpResponse.response
import static org.mockserver.model.JsonBody.json

@SpringBootTest
@MockServerTest("epis.service.url=http://localhost:\${mockServerPort}")
@TestPropertySource(properties = "spring.cache.type=SIMPLE")
class EpisServiceIntegrationSpec extends Specification {

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
    List<ApplicationSnapshot> applications = episService.getApplications(samplePerson())
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
            .withDelay(MILLISECONDS, 500)
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
      awaitTermination(5, SECONDS)
    }
    then:
    mockServerClient.verify(request().withPath("/applications"), VerificationTimes.exactly(1))
  }

  def "getCashFlowStatement - only one request per person is allowed to run at any time"() {
    given:
    mockServerClient
        .when(request("/account-cash-flow-statement")
            .withHeader("Authorization", "Bearer dummy"))
        .respond(response()
            .withDelay(MILLISECONDS, 500)
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
      awaitTermination(5, SECONDS)
    }
    then:
    mockServerClient.verify(request().withPath("/account-cash-flow-statement"), VerificationTimes.exactly(1))
  }

  def "account statement cache separates the represented person from the acting person"() {
    given:
    mockServerClient
        .when(request("/account-statement")
            .withHeader("Authorization", "Bearer dummy"))
        .respond(response()
            .withContentType(MediaType.APPLICATION_JSON)
            .withBody(json("[]", MatchType.STRICT)))

    def actingAsSelf = sampleAuthenticatedPersonAndMember().build()
    def representingChild = sampleAuthenticatedPersonAndMember()
        .role(new Role(PERSON, "61508110000", "Child Name"))
        .build()

    when:
    episService.getAccountStatement(actingAsSelf, null, null)
    episService.getAccountStatement(representingChild, null, null)

    then:
    mockServerClient.verify(request().withPath("/account-statement"), VerificationTimes.exactly(2))
  }

  def "account statement cache keys a legal entity role on the acting person, like epis-service does"() {
    given:
    mockServerClient
        .when(request("/account-statement")
            .withHeader("Authorization", "Bearer dummy"))
        .respond(response()
            .withContentType(MediaType.APPLICATION_JSON)
            .withBody(json("[]", MatchType.STRICT)))

    def actingAsSelf = sampleAuthenticatedPersonAndMember().build()
    def actingForCompany = sampleAuthenticatedPersonLegalEntity().build()

    when:
    episService.getAccountStatement(actingAsSelf, null, null)
    episService.getAccountStatement(actingForCompany, null, null)

    then:
    mockServerClient.verify(request().withPath("/account-statement"), VerificationTimes.exactly(1))
  }

  def "clearing the cache while representing a person evicts the represented person's entries"() {
    given:
    mockServerClient
        .when(request("/account-statement")
            .withHeader("Authorization", "Bearer dummy"))
        .respond(response()
            .withContentType(MediaType.APPLICATION_JSON)
            .withBody(json("[]", MatchType.STRICT)))

    def representingChild = sampleAuthenticatedPersonAndMember()
        .role(new Role(PERSON, "61508110000", "Child Name"))
        .build()

    when:
    episService.getAccountStatement(representingChild, null, null)
    episService.clearCache(representingChild)
    episService.getAccountStatement(representingChild, null, null)

    then:
    mockServerClient.verify(request().withPath("/account-statement"), VerificationTimes.exactly(2))
  }

  def "clear cache does not fail"() {
    when:
    episService.clearCache(samplePerson())
    then:
    noExceptionThrown()
  }

  def "can send mandates"() {
    given:
    def fundsTransferInstruction =
        new LegacyMandateSubmission.FundTransferInstruction("transferProcessId", 0.5, "EE123", "EE234", null)
    def address = countryFixture().build()
    def submission = LegacyMandateSubmission.builder()
        .id(111L)
        .fundTransferExchanges([fundsTransferInstruction])
        .processId("selectionProcessId")
        .createdDate(Instant.parse("2021-03-10T10:00:00Z"))
        .futureContributionFundIsin("EE345")
        .pillar(2)
        .address(address)
        .email("email@override.ee")
        .phoneNumber("+37288888888")
        .build()

    ApplicationResponseDTO mockedResponse = new ApplicationResponseDTO(
        ApplicationResponse.builder()
            .processId("responseProcessId")
            .applicationType(TRANSFER)
            .successful(true)
            .build()
    )
    MandateProcessResult expectedResult = MandateProcessResult.builder()
        .outcomes([new MandateProcessResult.MandateProcessOutcome("responseProcessId", true, null)])
        .build()

    mockServerClient
        .when(
            request()
                .withMethod("POST")
                .withPath("/mandates")
                .withBody(json("""
            {
              "id": ${submission.id()},
              "processId": "${submission.processId()}",
              "futureContributionFundIsin": "${submission.futureContributionFundIsin()}",
              "createdDate": "${submission.createdDate()}",
              "pillar": ${submission.pillar()},
              "fundTransferExchanges": [
                {
                  "processId": "${fundsTransferInstruction.processId()}",
                  "amount": ${fundsTransferInstruction.amount()},
                  "sourceFundIsin": "${fundsTransferInstruction.sourceFundIsin()}",
                  "targetFundIsin": "${fundsTransferInstruction.targetFundIsin()}",
                  "targetPik": ${fundsTransferInstruction.targetPik()}
                }
              ],
              "address": {
                "countryCode": "${address.countryCode}"
              },
              "email": "${submission.email()}",
              "phoneNumber": "${submission.phoneNumber()}",
              "paymentRate": ${submission.paymentRate()}
            }
          """, MatchType.STRICT))
        )
        .respond(
            response()
                .withContentType(MediaType.APPLICATION_JSON)
                .withBody(json(mockedResponse, MatchType.STRICT))
        )

    when:
    MandateProcessResult response = episService.sendMandate(submission)

    then:
    response == expectedResult
  }

  def "can send withdrawal cancellation mandate v2"() {
    given:
    def sampleCancellation = sampleWithdrawalCancellation()
    def mandateCommandResponse = sampleMandateCommandResponse("1", true, null, null)

    MandateCommandResponse expectedResponse = sampleMandateCommandResponse("1", true, null, null)

    mockServerClient
        .when(
            request()
                .withMethod("POST")
                .withPath("/mandates-v2")
                .withBody(json("""
            {
              "processId" : "${mandateCommandResponse.processId}",
              "mandateDto" : {
                "id" : ${sampleCancellation.id()},
                "details" : {
                  "mandateType" : "WITHDRAWAL_CANCELLATION"
                },
                "createdDate" : "${sampleCancellation.createdDate()}",
                "address" : {
                  "countryCode" : "${sampleCancellation.address().countryCode}"
                },
                "email" : "${sampleCancellation.email()}",
                "phoneNumber" : "${sampleCancellation.phoneNumber()}"
              }
            }
          """, MatchType.STRICT))
        )

        .respond(
            response()
                .withContentType(MediaType.APPLICATION_JSON)
                .withBody(json(expectedResponse, MatchType.STRICT))
        )

    when:
    MandateProcessResult response = episService.sendMandateV2(new MandateSubmissionCommand<>("1", sampleCancellation))

    then:
    def outcome = response.outcomes().first()
    outcome.successful() == expectedResponse.successful
    outcome.processId() == expectedResponse.processId
    outcome.errorCode() == expectedResponse.errorCode
  }

  def "can send early withdrawal cancellation mandate v2"() {
    given:
    def sampleCancellation = sampleEarlyWithdrawalCancellation()
    def mandateCommandResponse = sampleMandateCommandResponse("1", true, null, null)

    MandateCommandResponse expectedResponse = sampleMandateCommandResponse("1", true, null, null)

    mockServerClient
        .when(
            request()
                .withMethod("POST")
                .withPath("/mandates-v2")
                .withBody(json("""
            {
              "processId" : "${mandateCommandResponse.processId}",
              "mandateDto" : {
                "id" : ${sampleCancellation.id()},
                "details" : {
                  "mandateType" : "EARLY_WITHDRAWAL_CANCELLATION"
                },
                "createdDate" : "${sampleCancellation.createdDate()}",
                "address" : {
                  "countryCode" : "${sampleCancellation.address().countryCode}"
                },
                "email" : "${sampleCancellation.email()}",
                "phoneNumber" : "${sampleCancellation.phoneNumber()}"
              }
            }
          """, MatchType.STRICT))
        )

        .respond(
            response()
                .withContentType(MediaType.APPLICATION_JSON)
                .withBody(json(expectedResponse, MatchType.STRICT))
        )

    when:
    MandateProcessResult response = episService.sendMandateV2(new MandateSubmissionCommand<>("1", sampleCancellation))

    then:
    def outcome = response.outcomes().first()
    outcome.successful() == expectedResponse.successful
    outcome.processId() == expectedResponse.processId
    outcome.errorCode() == expectedResponse.errorCode
  }

  def "can send transfer cancellation mandate v2"() {
    given:

    def anIsin = "EE_TEST_ISIN_TO_CANCEL"
    def sampleCancellation = sampleTransferCancellation(anIsin, SECOND)
    def mandateCommandResponse = sampleMandateCommandResponse("1", true, null, null)

    MandateCommandResponse expectedResponse = sampleMandateCommandResponse("1", true, null, null)

    mockServerClient
        .when(
            request()
                .withMethod("POST")
                .withPath("/mandates-v2")
                .withBody(json("""
            {
              "processId" : "${mandateCommandResponse.processId}",
              "mandateDto" : {
                "id" : ${sampleCancellation.id()},
                "details" : {
                  "mandateType" : "TRANSFER_CANCELLATION",
                  "sourceFundIsinOfTransferToCancel" : "${anIsin}",
                  "pillar" : "SECOND"
                },
                "createdDate" : "${sampleCancellation.createdDate()}",
                "address" : {
                  "countryCode" : "${sampleCancellation.address().countryCode}"
                },
                "email" : "${sampleCancellation.email()}",
                "phoneNumber" : "${sampleCancellation.phoneNumber()}"
              }
            }
          """, MatchType.STRICT))
        )

        .respond(
            response()
                .withContentType(MediaType.APPLICATION_JSON)
                .withBody(json(expectedResponse, MatchType.STRICT))
        )

    when:
    MandateProcessResult response = episService.sendMandateV2(new MandateSubmissionCommand<>("1", sampleCancellation))

    then:
    def outcome = response.outcomes().first()
    outcome.successful() == expectedResponse.successful
    outcome.processId() == expectedResponse.processId
    outcome.errorCode() == expectedResponse.errorCode
  }

  def "can get contributions"() {
    given:
    def person = samplePerson()

    def expectedResponse = [new ThirdPillarContribution(
        Instant.parse("2023-04-26T10:00:00Z"),
        "Tuleva Fondid AS",
        12.34,
        EUR,
        3
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
