package ee.tuleva.onboarding.payment

import ee.tuleva.onboarding.currency.Currency
import ee.tuleva.onboarding.time.TestClockHolder

import java.time.Duration
import java.time.Instant

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.payment.PaymentData.Bank
import static ee.tuleva.onboarding.payment.PaymentData.PaymentType

class PaymentFixture {

  static BigDecimal aPaymentAmount = new BigDecimal("10.00")
  static BigDecimal contributionAmountHigh = new BigDecimal("10.01")
  static BigDecimal contributionAmountLow = new BigDecimal("9.99")
  static Currency aPaymentCurrency = Currency.EUR
  static PaymentType aPaymentType = PaymentType.SINGLE
  static Bank aPaymentBank = Bank.LHV
  static PaymentData aPaymentData = new PaymentData(aPaymentAmount, aPaymentCurrency, aPaymentType, aPaymentBank)
  static aPaymentCreationTime = TestClockHolder.now - Duration.ofDays(1)

  static Payment aNewPayment() {
    return new Payment(
        null, sampleUser().build(), UUID.fromString("3ab94f11-fb71-4401-8043-5e911227037e"), aPaymentAmount, Currency.EUR, null)
  }

  static Payment aPayment(Long id = 123L, Instant createdTime = aPaymentCreationTime) {
    return aNewPayment().tap {
      it.id = id
      it.createdTime = createdTime
    }
  }

}
