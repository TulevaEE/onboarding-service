package ee.tuleva.onboarding.payment.email

import ee.tuleva.onboarding.payment.Payment
import ee.tuleva.onboarding.user.User
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import spock.lang.Ignore
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.payment.PaymentFixture.aNewPayment

@SpringBootTest
@Ignore
class PaymentEmailServiceIntSpec extends Specification {
  @Autowired
  PaymentEmailService paymentEmailService

  def "SendThirdPillarPaymentSuccessEmail"() {
    given:
    User user = sampleUser().email("erko@risthein.ee").build()
    Payment payment = aNewPayment()

    when:
    paymentEmailService.sendThirdPillarPaymentSuccessEmail(user, payment, Locale.ENGLISH)
    paymentEmailService.sendThirdPillarPaymentSuccessEmail(user, payment, new Locale("et"))

    then:
    true
  }
}
