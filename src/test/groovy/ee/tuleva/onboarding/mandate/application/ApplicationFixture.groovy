package ee.tuleva.onboarding.mandate.application

import ee.tuleva.onboarding.epis.mandate.ApplicationStatus
import ee.tuleva.onboarding.fund.ApiFundResponse
import ee.tuleva.onboarding.payment.application.PaymentApplicationDetails
import ee.tuleva.onboarding.time.TestClockHolder

import java.time.Instant

import static ee.tuleva.onboarding.currency.Currency.EUR
import static ee.tuleva.onboarding.epis.mandate.ApplicationStatus.PENDING
import static ee.tuleva.onboarding.fund.FundFixture.tuleva3rdPillarFund
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleFunds
import static ee.tuleva.onboarding.mandate.application.Application.ApplicationBuilder
import static ee.tuleva.onboarding.mandate.application.Application.builder
import static ee.tuleva.onboarding.mandate.application.TransferApplicationDetails.TransferApplicationDetailsBuilder

class ApplicationFixture {

  static ApplicationBuilder sampleApplication() {
    return builder()
  }

  static ApplicationBuilder transferApplication() {
    return sampleApplication()
        .creationTime(Instant.now(TestClockHolder.clock))
        .status(PENDING)
        .id(123L)
        .details(transferApplicationDetails().build())
  }

  static ApplicationBuilder withdrawalApplication() {
    return sampleApplication()
        .creationTime(Instant.now(TestClockHolder.clock))
        .status(PENDING)
        .id(123L)
        .details(withdrawalApplicationDetails().build())
  }

  static ApplicationBuilder paymentApplication() {
    return sampleApplication()
        .creationTime(Instant.now(TestClockHolder.clock))
        .status(PENDING)
        .id(123L)
        .details(paymentApplicationDetails().build())
  }

  static TransferApplicationDetailsBuilder transferApplicationDetails() {
    def sourceFund = new ApiFundResponse(sampleFunds().first(), Locale.ENGLISH)
    def targetFund = new ApiFundResponse(sampleFunds().drop(1).first(), Locale.ENGLISH)

    return TransferApplicationDetails.builder()
        .sourceFund(sourceFund)
        .exchange(Exchange.builder()
            .sourceFund(sourceFund)
            .targetFund(targetFund)
            .amount(1.0)
            .build()
        )
  }

  static Application<PaymentRateApplicationDetails> samplePendingPaymentRateApplication() {
    return Application.builder()
        .creationTime(Instant.now())
        .status(PENDING)
        .id(123L)
        .details(PaymentRateApplicationDetails.builder()
            .paymentRate(BigDecimal.valueOf(6))
            .type(ApplicationType.PAYMENT_RATE)
            .build())
        .build()
  }

  static Application<PaymentRateApplicationDetails> sampleCompletedPaymentRateApplication(BigDecimal rate) {
    return Application.builder()
        .creationTime(Instant.now())
        .status(ApplicationStatus.COMPLETE)
        .id(123L)
        .details(PaymentRateApplicationDetails.builder()
            .paymentRate(rate)
            .type(ApplicationType.PAYMENT_RATE)
            .build())
        .build()
  }

  static Application<PaymentRateApplicationDetails> sampleCompletedPaymentRateApplication(BigDecimal rate, Instant creationTime) {
    return Application.builder()
        .creationTime(creationTime)
        .status(ApplicationStatus.COMPLETE)
        .id(123L)
        .details(PaymentRateApplicationDetails.builder()
            .paymentRate(rate)
            .type(ApplicationType.PAYMENT_RATE)
            .build())
        .build()
  }

  static WithdrawalApplicationDetails.WithdrawalApplicationDetailsBuilder withdrawalApplicationDetails() {
    return WithdrawalApplicationDetails.builder()
        .depositAccountIBAN("IBAN")
  }

  static PaymentApplicationDetails.PaymentApplicationDetailsBuilder paymentApplicationDetails() {
    return PaymentApplicationDetails.builder()
        .amount(10.0)
        .currency(EUR)
        .targetFund(new ApiFundResponse(tuleva3rdPillarFund(), Locale.ENGLISH))
  }
}
