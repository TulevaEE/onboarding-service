package ee.tuleva.onboarding.analytics.paymentrate


import java.time.LocalDateTime

import static ee.tuleva.onboarding.analytics.paymentrate.PaymentRateAbandonment.PaymentRateAbandonmentBuilder
import static ee.tuleva.onboarding.analytics.paymentrate.PaymentRateAbandonment.builder

class PaymentRateAbandonmentFixture {

  static PaymentRateAbandonmentBuilder aPaymentRateAbandonment() {
    builder()
        .personalCode(uniquePersonalCode(0))
        .firstName("John")
        .lastName("Doe")
        .email(uniqueEmail(0))
        .language("EST")
        .lastEmailSentDate(LocalDateTime.parse("2024-01-15T10:00:00"))
        .count(5)
        .path("/2nd-pillar-payment-rate")
        .currentRate(2)
        .pendingRate(null)
        .pendingRateDate(null)
  }

  static String uniquePersonalCode(int uniqueId) {
    "385103095${String.format('%02d', uniqueId)}"
  }

  static String uniqueEmail(int uniqueId) {
    "john.doe${uniqueId}@example.com"
  }
}
