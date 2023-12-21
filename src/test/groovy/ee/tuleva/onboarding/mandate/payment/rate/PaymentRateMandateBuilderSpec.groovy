package ee.tuleva.onboarding.mandate.payment.rate

import ee.tuleva.onboarding.mandate.Mandate
import ee.tuleva.onboarding.mandate.builder.ConversionDecorator
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.fullyConverted
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture

class PaymentRateMandateBuilderSpec extends Specification {

  def conversionDecorator = Mock(ConversionDecorator)
  def paymentRateMandateBuilder = new PaymentRateMandateBuilder(conversionDecorator)

  def "build creates mandate with given payment rate"() {
    given:
    BigDecimal paymentRate = new BigDecimal("2.0")
    def authenticatedPerson = sampleAuthenticatedPersonAndMember().build()
    def user = sampleUser().build()
    def conversion = fullyConverted()
    def contactDetails = contactDetailsFixture()

    when:
    Mandate mandate = paymentRateMandateBuilder.build(paymentRate, authenticatedPerson, user, conversion, contactDetails)

    then:
    mandate.paymentRate == paymentRate
    mandate.user == user
    mandate.address == contactDetails.address
    1 * conversionDecorator.addConversionMetadata(_, conversion, contactDetails, authenticatedPerson)
  }
}
