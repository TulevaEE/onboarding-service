package ee.tuleva.onboarding.payment

import java.time.Instant

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.currency.Currency.*
import static ee.tuleva.onboarding.payment.PaymentStatus.PENDING

class PaymentFixture {

  static BigDecimal pendingPaymentAmount = new BigDecimal("10.00")
  static BigDecimal contributionAmountHigh = new BigDecimal("10.01")
  static BigDecimal contributionAmountLow = new BigDecimal("9.99")

  static Payment aNewPayment() {
    return new Payment(
        null, sampleUser().build(), UUID.fromString("3ab94f11-fb71-4401-8043-5e911227037e"), pendingPaymentAmount, EUR, PENDING, null)
  }

  static Payment aPendingPayment(Long id = 123L) {
    return aNewPayment().tap {
      it.id = id
      createdTime = Instant.parse("2022-09-29T10:15:30Z")
    }
  }

}
