package ee.tuleva.onboarding.payment


import ee.tuleva.onboarding.currency.Currency
import ee.tuleva.onboarding.time.TestClockHolder
import ee.tuleva.onboarding.user.User

import java.time.Duration
import java.time.Instant

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember
import static ee.tuleva.onboarding.payment.PaymentData.Bank
import static ee.tuleva.onboarding.payment.PaymentData.PaymentType

class PaymentFixture {

  static BigDecimal aPaymentAmount = new BigDecimal("10.00")
  static BigDecimal contributionAmountHigh = new BigDecimal("10.01")
  static BigDecimal contributionAmountLow = new BigDecimal("9.99")
  static Currency aPaymentCurrency = Currency.EUR
  static PaymentType aPaymentType = PaymentType.SINGLE
  static Bank aPaymentBank = Bank.LHV
  static User sampleUserNonMember = sampleUserNonMember().build()
  static PaymentData aPaymentData = new PaymentData(sampleUser.personalCode, aPaymentAmount, aPaymentCurrency, aPaymentType, aPaymentBank)
  static PaymentData aPaymentDataWithoutAnAmount = new PaymentData(sampleUser.personalCode, null, aPaymentCurrency, aPaymentType, aPaymentBank)
  static PaymentData aPaymentDataForMemberPayment = new PaymentData(sampleUserNonMember.personalCode, aPaymentAmount, aPaymentCurrency, PaymentType.MEMBER_FEE, Bank.TULUNDUSUHISTU)
  static aPaymentCreationTime = TestClockHolder.now - Duration.ofDays(1)

  static Payment aNewSinglePayment() {
    return new Payment(
        null, sampleUser, UUID.fromString("3ab94f11-fb71-4401-8043-5e911227037e"), aPaymentAmount, Currency.EUR, sampleUser.personalCode, null, PaymentType.SINGLE)
  }

  static Payment aNewMemberPayment() {
    return new Payment(
        null, sampleUserNonMember, UUID.fromString("3ab94f11-fb71-4401-8043-5e911227037e"), aPaymentAmount, Currency.EUR, sampleUser.personalCode, null, PaymentType.MEMBER_FEE)
  }

  static Payment aNewMemberPaymentForExistingMember() {
    return new Payment(
        null, sampleUser, UUID.fromString("3ab94f11-fb71-4401-8043-5e911227037e"), aPaymentAmount, Currency.EUR, sampleUser.personalCode, null, PaymentType.MEMBER_FEE)
  }

  static Payment aPayment(Long id = 123L, Instant createdTime = aPaymentCreationTime) {
    return aNewSinglePayment().tap {
      it.id = id
      it.createdTime = createdTime
    }
  }

}
