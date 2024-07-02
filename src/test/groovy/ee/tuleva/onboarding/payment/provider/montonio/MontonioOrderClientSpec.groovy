import com.fasterxml.jackson.databind.ObjectMapper
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.Payload
import com.nimbusds.jose.crypto.MACSigner
import ee.tuleva.onboarding.currency.Currency
import ee.tuleva.onboarding.payment.PaymentData
import ee.tuleva.onboarding.payment.provider.PaymentProviderChannel
import ee.tuleva.onboarding.payment.provider.PaymentProviderFixture
import ee.tuleva.onboarding.payment.provider.montonio.*
import org.eclipse.persistence.oxm.MediaType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest
import org.springframework.test.web.client.MockRestServiceServer
import spock.lang.Specification

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

@RestClientTest(MontonioOrderClient)
class MontonioOrderClientSpec extends Specification {

  @Autowired
  MontonioOrderClient montonioOrderClient

  @Autowired
  MockRestServiceServer server

  def aPaymentUrl = "http://payment.url"
  def objectMapper = Mock(ObjectMapper)
  def paymentProviderConfiguration = PaymentProviderFixture.aPaymentProviderConfiguration()

  def "getPaymentLink should return payment URL"() {
    given:
    def order = MontonioOrder.builder()
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

    def paymentData = PaymentData.builder()
        .recipientPersonalCode("testID")
        .amount(BigDecimal.valueOf(100.00))
        .currency(Currency.EUR)
        .type(PaymentData.PaymentType.SINGLE)
        .paymentChannel(PaymentData.PaymentChannel.LHV)
        .build()

    // def paymentProviderChannel = new PaymentProviderChannel(secretKey: "testSecrettestSecrettestSecrettestSecrettestSecrettestSecrettestSecrettestSecret")
    def jwsObject = new JWSObject(new JWSHeader(JWSAlgorithm.HS256), new Payload("orderJson"))
    def orderResponse = new MontonioApiClient.MontonioOrderResponse(aPaymentUrl)

    /*def requestBodyUriSpec = Mock(RequestBodyUriSpec)
    def requestBodySpec = Mock(RequestBodySpec)
    def requestHeadersSpec = Mock(RequestHeadersSpec)
    def responseSpec = Mock(ResponseSpec)*/

    server.expect(requestTo(aPaymentUrl)).andRespond(withSuccess(orderResponse, MediaType.APPLICATION_JSON))

    and:
    // paymentProviderConfiguration.getPaymentProviderChannel("LHV") >> paymentProviderChannel
    1 * objectMapper.writeValueAsString(order) >> "orderJson"
    objectMapper.writeValueAsString(_ as Map) >> "signedPayload"
    /*montonioClient.post() >> requestBodyUriSpec
    requestBodyUriSpec.uri("orders") >> requestBodySpec
    requestBodySpec.body("signedPayload") >> responseSpec
    // requestHeadersSpec.retrieve() >> responseSpec
    responseSpec.body(MontonioOrderResponse.class) >> orderResponse*/
    jwsObject.sign(new MACSigner(paymentProviderConfiguration.getPaymentProviderChannel(PaymentData.PaymentChannel.LHV).getSecretKey().getBytes())) >> _

    when:
    def paymentLink = montonioOrderClient.getPaymentUrl(order, paymentData)

    then:
    paymentLink == aPaymentUrl
//    1 * paymentProviderConfiguration.getPaymentProviderChannel("LHV")
//    1 * objectMapper.writeValueAsString(order)
//    1 * objectMapper.writeValueAsString(_ as Map)
//    1 * montonioClient.post()
//    1 * requestBodyUriSpec.uri("orders")
//    1 * requestBodySpec.body("signedPayload")
//    1 * requestHeadersSpec.retrieve()
//    1 * responseSpec.body(MontonioOrderResponse.class)
  }

  def "getSignedOrderPayload should return signed payload"() {
    given:
    def order = MontonioOrder.builder()
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

    def paymentData = PaymentData.builder()
        .recipientPersonalCode("testID")
        .amount(BigDecimal.valueOf(100.00))
        .currency(Currency.EUR)
        .type(PaymentData.PaymentType.SINGLE)
        .paymentChannel(PaymentData.PaymentChannel.LHV)
        .build()

    def paymentProviderChannel = new PaymentProviderChannel(secretKey: "testSecrettestSecrettestSecrettestSecrettestSecrettestSecrettestSecret")
    def jwsObject = new JWSObject(new JWSHeader(JWSAlgorithm.HS256), new Payload("orderJson"))

    and:
    paymentProviderConfiguration.getPaymentProviderChannel("LHV") >> paymentProviderChannel
    objectMapper.writeValueAsString(order) >> "orderJson"
    objectMapper.writeValueAsString(_ as Map) >> "signedPayload"
    jwsObject.sign(new MACSigner(paymentProviderChannel.getSecretKey().getBytes())) >> _

    when:
    def signedPayload = montonioOrderClient.getSignedOrderPayload(order, paymentData)

    then:
    signedPayload == "signedPayload"
    1 * paymentProviderConfiguration.getPaymentProviderChannel("LHV")
    1 * objectMapper.writeValueAsString(order)
    1 * objectMapper.writeValueAsString(_ as Map)
  }
}
