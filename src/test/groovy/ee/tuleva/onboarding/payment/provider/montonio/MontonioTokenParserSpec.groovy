package ee.tuleva.onboarding.payment.provider.montonio

import com.fasterxml.jackson.databind.ObjectMapper
import com.nimbusds.jose.JWSObject
import ee.tuleva.onboarding.payment.PaymentData
import ee.tuleva.onboarding.payment.provider.PaymentReference
import org.springframework.security.authentication.BadCredentialsException
import spock.lang.Specification

import static ee.tuleva.onboarding.payment.provider.PaymentProviderFixture.aPaymentProviderConfiguration
import static ee.tuleva.onboarding.payment.provider.PaymentProviderFixture.aSecretKey
import static ee.tuleva.onboarding.payment.provider.PaymentProviderFixture.aSerializedSavingsPaymentToken
import static ee.tuleva.onboarding.payment.provider.PaymentProviderFixture.aSerializedSinglePaymentFinishedToken
import static ee.tuleva.onboarding.payment.provider.PaymentProviderFixture.getAnInvalidSinglePaymentFinishedToken

class MontonioTokenParserSpec extends Specification {
  MontonioTokenParser montonioTokenParser

  void setup() {
    montonioTokenParser = new MontonioTokenParser(
        new ObjectMapper(),
        aPaymentProviderConfiguration(),
    )
  }

  def "rejects payment token with invalid signature"() {
    given:
    def token = anInvalidSinglePaymentFinishedToken
    when:
    montonioTokenParser.verifyToken(JWSObject.parse(token))
    then:
    thrown(BadCredentialsException)
  }

  def "rejects token when secret doesn't match"() {
    given:
    def token = aSerializedSinglePaymentFinishedToken
    when:
    montonioTokenParser.verifyToken(JWSObject.parse(token), aSecretKey + "1")
    then:
    thrown(BadCredentialsException)
  }

  def "parses token correctly"() {
    given:
    def token = aSerializedSavingsPaymentToken
    when:
    def parsed = montonioTokenParser.parse(JWSObject.parse(token))
    then:
    parsed.uuid == "3ab94f11-fb71-4401-8043-5e911227037e"
    parsed.merchantReference == new PaymentReference("38812121215", "38812121215", UUID.fromString("3ab94f11-fb71-4401-8043-5e911227037e"), PaymentData.PaymentType.SAVINGS, Locale.ENGLISH, "description")
    parsed.grandTotal == new BigDecimal("10.00")
    parsed.paymentStatus == MontonioOrderToken.MontonioOrderStatus.PAID
  }
}
