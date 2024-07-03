package ee.tuleva.onboarding.payment


import ee.tuleva.onboarding.currency.Currency
import ee.tuleva.onboarding.payment.provider.montonio.MontonioOrder
import ee.tuleva.onboarding.payment.provider.montonio.MontonioPaymentChannel
import ee.tuleva.onboarding.payment.provider.montonio.MontonioPaymentMethod
import ee.tuleva.onboarding.payment.provider.montonio.MontonioPaymentMethodOptions
import ee.tuleva.onboarding.time.TestClockHolder
import ee.tuleva.onboarding.user.User

import java.time.Duration
import java.time.Instant

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember
import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel
import static ee.tuleva.onboarding.payment.PaymentData.PaymentType

class PaymentFixture {

  static BigDecimal aPaymentAmount = new BigDecimal("10.00")
  static BigDecimal contributionAmountHigh = new BigDecimal("10.01")
  static BigDecimal contributionAmountLow = new BigDecimal("9.99")
  static Currency aPaymentCurrency = Currency.EUR
  static PaymentType aPaymentType = PaymentType.SINGLE
  static PaymentChannel aPaymentChannel = PaymentChannel.LHV
  static User sampleUserNonMember = sampleUserNonMember().build()
  static PaymentData aPaymentData = aPaymentData()
  static PaymentData aPaymentDataWithoutAnAmount = new PaymentData(sampleUser.personalCode, null, aPaymentCurrency, aPaymentType, aPaymentChannel)
  static PaymentData aPaymentDataForMemberPayment = new PaymentData(sampleUserNonMember.personalCode, aPaymentAmount, aPaymentCurrency, PaymentType.MEMBER_FEE, PaymentChannel.TULUNDUSUHISTU)
  static aPaymentCreationTime = TestClockHolder.now - Duration.ofDays(1)

  static MontonioOrder aMontonioOrder = MontonioOrder.builder()
      .accessKey("testAccessKey")
      .merchantReference("testMerchantReference")
      .returnUrl("http://return.url")
      .notificationUrl("http://notification.url")
      .grandTotal(BigDecimal.valueOf(100.00))
      .currency(Currency.EUR)
      .exp(BigDecimal.valueOf(System.currentTimeMillis() / 1000L + 3600L).toLong())
      .payment(MontonioPaymentMethod.builder()
          .amount(BigDecimal.valueOf(100.00))
          .currency(Currency.EUR)
          .methodOptions(MontonioPaymentMethodOptions.builder()
              .preferredProvider("testProvider")
              .preferredLocale("en")
              .paymentDescription("testPayment")
              .build())
          .build())
      .locale("en")
      .build()

  static PaymentData aPaymentData() {
    return new PaymentData(sampleUser.personalCode, aPaymentAmount, aPaymentCurrency, aPaymentType, aPaymentChannel)
  }

  static PaymentData aNewMemberPaymentData() {
    return new PaymentData(sampleUser.personalCode, aPaymentAmount, aPaymentCurrency, PaymentType.MEMBER_FEE, aPaymentChannel)
  }

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

  static MontonioPaymentChannel aMontonioPaymentChannel() {
    return new MontonioPaymentChannel(
        accessKey: "an-access-key",
        bic: "a-bic"
    )
  }

}
