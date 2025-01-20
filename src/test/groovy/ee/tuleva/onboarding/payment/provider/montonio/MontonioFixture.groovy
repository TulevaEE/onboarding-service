package ee.tuleva.onboarding.payment.provider.montonio


import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.currency.Currency.EUR
import static ee.tuleva.onboarding.payment.provider.montonio.MontonioOrder.MontonioPaymentMethod
import static ee.tuleva.onboarding.payment.provider.montonio.MontonioOrder.MontonioPaymentMethod.MontonioPaymentMethodOptions

class MontonioFixture {

  static MontonioOrder aMontonioOrder = MontonioOrder.builder()
      .accessKey("testAccessKey")
      .merchantReference("testMerchantReference")
      .returnUrl("http://return.url")
      .notificationUrl("http://notification.url")
      .grandTotal(100.00)
      .currency(EUR)
      .exp(BigDecimal.valueOf(System.currentTimeMillis() / 1000L + 3600L).toLong())
      .payment(MontonioPaymentMethod.builder()
          .amount(100.00)
          .currency(EUR)
          .methodOptions(MontonioPaymentMethodOptions.builder()
              .preferredProvider("testProvider")
              .preferredLocale("en")
              .paymentDescription("testPayment")
              .build())
          .build())
      .billingAddress(MontonioOrder.MontonioBillingAddress.builder()
          .firstName(samplePerson.firstName)
          .lastName(samplePerson.lastName)
          .build())
      .locale("en")
      .build()

}
