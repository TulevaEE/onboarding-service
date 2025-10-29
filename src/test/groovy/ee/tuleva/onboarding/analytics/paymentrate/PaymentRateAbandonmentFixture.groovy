package ee.tuleva.onboarding.analytics.paymentrate

import java.time.LocalDate
import java.time.LocalDateTime

class PaymentRateAbandonmentFixture {

  static PaymentRateAbandonmentBuilder aPaymentRateAbandonment() {
    new PaymentRateAbandonmentBuilder()
  }

  static class PaymentRateAbandonmentBuilder {
    private int uniqueId = 0
    private String personalCode = null
    private String firstName = "John"
    private String lastName = "Doe"
    private String email = null
    private String language = "EST"
    private LocalDateTime lastEmailSentDate = LocalDateTime.parse("2024-01-15T10:00:00")
    private Integer count = 5
    private String path = "/2nd-pillar-payment-rate"
    private Integer currentRate = 2
    private Integer pendingRate = null
    private LocalDate pendingRateDate = null

    PaymentRateAbandonmentBuilder withUniqueId(int uniqueId) {
      this.uniqueId = uniqueId
      return this
    }

    PaymentRateAbandonmentBuilder withPersonalCode(String personalCode) {
      this.personalCode = personalCode
      return this
    }

    PaymentRateAbandonmentBuilder withFirstName(String firstName) {
      this.firstName = firstName
      return this
    }

    PaymentRateAbandonmentBuilder withLastName(String lastName) {
      this.lastName = lastName
      return this
    }

    PaymentRateAbandonmentBuilder withEmail(String email) {
      this.email = email
      return this
    }

    PaymentRateAbandonmentBuilder withLanguage(String language) {
      this.language = language
      return this
    }

    PaymentRateAbandonmentBuilder withLastEmailSentDate(LocalDateTime lastEmailSentDate) {
      this.lastEmailSentDate = lastEmailSentDate
      return this
    }

    PaymentRateAbandonmentBuilder withoutLastEmailSentDate() {
      this.lastEmailSentDate = null
      return this
    }

    PaymentRateAbandonmentBuilder withCount(Integer count) {
      this.count = count
      return this
    }

    PaymentRateAbandonmentBuilder withPath(String path) {
      this.path = path
      return this
    }

    PaymentRateAbandonmentBuilder withCurrentRate(Integer currentRate) {
      this.currentRate = currentRate
      return this
    }

    PaymentRateAbandonmentBuilder withPendingRate(Integer pendingRate) {
      this.pendingRate = pendingRate
      return this
    }

    PaymentRateAbandonmentBuilder withPendingRateDate(LocalDate pendingRateDate) {
      this.pendingRateDate = pendingRateDate
      return this
    }

    PaymentRateAbandonmentBuilder withPendingRate(Integer pendingRate, LocalDate pendingRateDate) {
      this.pendingRate = pendingRate
      this.pendingRateDate = pendingRateDate
      return this
    }

    PaymentRateAbandonment build() {
      def code = personalCode ?: "385103095${String.format('%02d', uniqueId)}"
      def emailAddress = email ?: "john.doe${uniqueId}@example.com"

      return new PaymentRateAbandonment(
          code,
          firstName,
          lastName,
          emailAddress,
          language,
          lastEmailSentDate,
          count,
          path,
          currentRate,
          pendingRate,
          pendingRateDate
      )
    }
  }
}
