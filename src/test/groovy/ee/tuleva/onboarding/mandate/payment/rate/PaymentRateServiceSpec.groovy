package ee.tuleva.onboarding.mandate.payment.rate

import ee.tuleva.onboarding.conversion.ConversionResponse
import ee.tuleva.onboarding.conversion.UserConversionService
import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.epis.contact.ContactDetails
import ee.tuleva.onboarding.mandate.Mandate
import ee.tuleva.onboarding.mandate.MandateService
import ee.tuleva.onboarding.user.User
import ee.tuleva.onboarding.user.UserService
import org.junit.jupiter.api.Test
import spock.lang.Specification

import static org.mockito.Mockito.*
import static org.assertj.core.api.Assertions.assertThat

class PaymentRateServiceSpec extends Specification {
  UserService userService = Mock(UserService)
  UserConversionService conversionService = Mock(UserConversionService)
  EpisService episService = Mock(EpisService)
  PaymentRateMandateBuilder paymentRateMandateBuilder = Mock(PaymentRateMandateBuilder)
  MandateService mandateService = Mock(MandateService)

  PaymentRateService paymentRateService = new PaymentRateService(
      mandateService, userService, episService, conversionService, paymentRateMandateBuilder)

  @Test
  void "savePaymentRateMandate creates and saves a mandate for the user"() {
    Long userId = 123L
    BigDecimal paymentRate = new BigDecimal("2.0")
    User user = mock(User.class)
    ConversionResponse conversion = mock(ConversionResponse.class)
    ContactDetails contactDetails = mock(ContactDetails.class)
    Mandate mandate = mock(Mandate.class)

    given:
    userService.getById(userId) >> user
    conversionService.getConversion(user) >> conversion
    episService.getContactDetails(user) >> contactDetails
    paymentRateMandateBuilder.build(paymentRate, user, conversion, contactDetails) >> mandate
    mandateService.save(user, mandate) >> mandate

    when:
    Mandate result = paymentRateService.savePaymentRateMandate(userId, paymentRate)

    then:
    assertThat(result).isSameAs(mandate)
  }
}
