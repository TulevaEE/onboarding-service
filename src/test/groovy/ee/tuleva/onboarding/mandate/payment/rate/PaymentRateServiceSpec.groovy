package ee.tuleva.onboarding.mandate.payment.rate

import ee.tuleva.onboarding.conversion.ConversionResponse
import ee.tuleva.onboarding.conversion.UserConversionService
import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.epis.contact.ContactDetails
import ee.tuleva.onboarding.mandate.Mandate
import ee.tuleva.onboarding.mandate.MandateService
import ee.tuleva.onboarding.user.User
import ee.tuleva.onboarding.user.UserService
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate

class PaymentRateServiceSpec extends Specification {
  UserService userService = Mock(UserService)
  UserConversionService conversionService = Mock(UserConversionService)
  EpisService episService = Mock(EpisService)
  PaymentRateMandateBuilder paymentRateMandateBuilder = Mock(PaymentRateMandateBuilder)
  MandateService mandateService = Mock(MandateService)

  PaymentRateService paymentRateService = new PaymentRateService(
      mandateService, userService, episService, conversionService, paymentRateMandateBuilder)


  def "can change payment rate"() {
    given:
    BigDecimal paymentRate = new BigDecimal("2.0")
    User user = sampleUser().build()
    Long userId = user.getId()
    ConversionResponse conversion = Mock(ConversionResponse)
    ContactDetails contactDetails = Mock(ContactDetails)
    Mandate mandate = sampleMandate()

    userService.getById(userId) >> user
    conversionService.getConversion(user) >> conversion
    episService.getContactDetails(user) >> contactDetails
    1 * paymentRateMandateBuilder.build(paymentRate, user, conversion, contactDetails) >> mandate
    1 * mandateService.save(user, mandate) >> mandate

    when:
    Mandate result = paymentRateService.savePaymentRateMandate(userId, paymentRate)

    then:
    result.id == mandate.id
  }

}
