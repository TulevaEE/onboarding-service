import tools.jackson.databind.json.JsonMapper
import ee.tuleva.onboarding.currency.Currency
import ee.tuleva.onboarding.payment.PaymentData
import ee.tuleva.onboarding.payment.provider.PaymentProviderFixture
import ee.tuleva.onboarding.payment.provider.montonio.*
import spock.lang.Specification

import static ee.tuleva.onboarding.payment.provider.montonio.MontonioFixture.aMontonioOrder

class MontonioOrderClientSpec extends Specification {

  def objectMapper = Mock(JsonMapper)
  def montonioApiClient = Mock(MontonioApiClient)
  def paymentProviderConfiguration = PaymentProviderFixture.aPaymentProviderConfiguration()

  def montonioOrderClient = new MontonioOrderClient(objectMapper, montonioApiClient, paymentProviderConfiguration)

  def aPaymentUrl = "http://payment.url"

  def "getPaymentLink should return payment URL"() {
    given:

    def paymentData = PaymentData.builder()
        .recipientPersonalCode("testID")
        .amount(BigDecimal.valueOf(100.00))
        .currency(Currency.EUR)
        .type(PaymentData.PaymentType.SINGLE)
        .paymentChannel(PaymentData.PaymentChannel.LHV)
        .build()

    1 * objectMapper.writeValueAsString(aMontonioOrder) >> "orderJson"
    1 * montonioApiClient.getPaymentUrl([data: "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.b3JkZXJKc29u.34o8wPIfdGuY-lthnvYtMNPcprmmAKVLI_mmQ3I0EBY"]) >> "http://payment.url"

    when:
    def paymentLink = montonioOrderClient.getPaymentUrl(aMontonioOrder, paymentData)

    then:
    paymentLink == aPaymentUrl
  }
}
