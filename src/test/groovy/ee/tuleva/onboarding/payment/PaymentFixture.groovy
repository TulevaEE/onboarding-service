package ee.tuleva.onboarding.payment

import java.time.Instant

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.currency.Currency.*

class PaymentFixture {

  static BigDecimal paymentAmount = new BigDecimal("10.00")
  static BigDecimal contributionAmountHigh = new BigDecimal("10.01")
  static BigDecimal contributionAmountLow = new BigDecimal("9.99")

  static Payment aNewPayment() {
    return new Payment(
        null, sampleUser().build(), UUID.fromString("3ab94f11-fb71-4401-8043-5e911227037e"), paymentAmount, EUR, null)
  }

  static Payment aPayment(Long id = 123L) {
    return aNewPayment().tap {
      it.id = id
      createdTime = Instant.parse("2022-09-29T10:15:30Z")
    }
  }

}
