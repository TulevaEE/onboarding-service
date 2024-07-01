package ee.tuleva.onboarding.payment.provider.montonio

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.NAVCheckValueRetriever
import ee.tuleva.onboarding.currency.Currency
import ee.tuleva.onboarding.payment.PaymentData
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import spock.lang.Specification

import java.time.LocalDate

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

@RestClientTest(MontonioApiClient)
class MontonioApiClientSpec extends Specification {

  @Autowired
  MontonioApiClient montonioApiClient

  @Autowired
  MockRestServiceServer server

  def cleanup() {
    server.reset()
  }

  def "it successfully fetches payment URL"() {
    given:
    def anOrder = MontonioOrder.builder()
        .accessKey("testAccessKey")
        .merchantReference("testMerchantReference")
        .returnUrl("http://return.url")
        .notificationUrl("http://notification.url")
        .grandTotal(BigDecimal.valueOf(100.00))
        .currency(Currency.EUR)
        .exp(BigDecimal.valueOf(System.currentTimeMillis() / 1000L + 3600L).toLong())
        .payment(MontonioPaymentMethod.builder()
            .amount(BigDecimal.valueOf(100.00))
            .currency(Currency.EUR)
            .methodOptions(MontonioPaymentMethodOptions.builder()
                .preferredProvider("testProvider")
                .preferredLocale("en")
                .paymentDescription("testPayment")
                .build())
            .build())
        .locale("en")
        .build()

    def aPaymentData = PaymentData.builder()
        .recipientPersonalCode("testID")
        .amount(BigDecimal.valueOf(100.00))
        .currency(Currency.EUR)
        .type(PaymentData.PaymentType.SINGLE)
        .paymentChannel(PaymentData.PaymentChannel.LHV)
        .build()
    def mockApiResponse = """{"paymentUrl": "payment.url"}"""

    server.expect(requestTo("orders"))
        .andRespond(withSuccess(mockApiResponse, MediaType.APPLICATION_JSON))

    when:
    def result = montonioApiClient.getPaymentLink(anOrder, aPaymentData)

    then:
    result == 'payment.url'
  }
}
