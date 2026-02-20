package ee.tuleva.onboarding.payment.provider.montonio


import ee.tuleva.onboarding.currency.Currency
import ee.tuleva.onboarding.payment.PaymentData
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import spock.lang.Specification

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

@RestClientTest(MontonioApiClient)
class MontonioApiClientSpec extends Specification {

  @Autowired
  MontonioApiClient montonioApiClient

  @Autowired
  MockRestServiceServer server

  @Value('${payment-provider.url}')
  private String montonioUrl

  def cleanup() {
    server.reset()
  }

  def "it successfully fetches payment URL"() {
    given:

    def mockPayload = Map.of("data", "testJwt")
    def mockApiResponse = """{"paymentUrl": "payment.url"}"""

    server.expect(requestTo(montonioUrl + "/orders"))
        .andRespond(withSuccess(mockApiResponse, MediaType.APPLICATION_JSON))

    when:
    def result = montonioApiClient.getPaymentUrl(mockPayload)

    then:
    result == 'payment.url'
  }
}
