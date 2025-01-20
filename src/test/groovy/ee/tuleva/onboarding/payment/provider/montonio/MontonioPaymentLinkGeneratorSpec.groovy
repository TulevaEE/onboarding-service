package ee.tuleva.onboarding.payment.provider.montonio

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import ee.tuleva.onboarding.payment.PaymentLink
import spock.lang.Specification

import static ee.tuleva.onboarding.payment.PaymentFixture.aPaymentData
import static ee.tuleva.onboarding.payment.provider.montonio.MontonioFixture.*

class MontonioPaymentLinkGeneratorSpec extends Specification {


  def montonioOrderCreator = Mock(MontonioOrderCreator)
  def monotonioOrderClient = Mock(MontonioOrderClient)

  def montonioPaymentLinkGenerator = new MontonioPaymentLinkGenerator(montonioOrderCreator, monotonioOrderClient)

  def "can get a payment link"() {
    def aPaymentUrl = "https://payment.url"

    given:
    1 * montonioOrderCreator.getOrder(aPaymentData(), sampleAuthenticatedPerson) >> aMontonioOrder
    1 * monotonioOrderClient.getPaymentUrl(aMontonioOrder, aPaymentData()) >> aPaymentUrl

    when:
    PaymentLink paymentLink = montonioPaymentLinkGenerator.getPaymentLink(aPaymentData, sampleAuthenticatedPerson)

    then:
    paymentLink.url() == "https://payment.url"
  }

  AuthenticatedPerson sampleAuthenticatedPerson = AuthenticatedPerson.builder()
      .firstName("Jordan")
      .lastName("Valdma")
      .personalCode("38501010000")
      .userId(2L)
      .build()

}
