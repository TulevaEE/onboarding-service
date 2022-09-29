package ee.tuleva.onboarding.payment

import java.time.Instant

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.currency.Currency.*
import static ee.tuleva.onboarding.payment.PaymentStatus.PENDING

class PaymentFixture {

  static Payment aNewPayment() {
    return new Payment(
        null, sampleUser().build(), UUID.fromString("3ab94f11-fb71-4401-8043-5e911227037e"), new BigDecimal("10.00"), EUR, PENDING, null)
  }

  static Payment aPendingPayment() {
    return aNewPayment().tap {
      id = 123L
      createdTime = Instant.parse("2022-09-29T10:15:30Z")
    }
  }

}
