package ee.tuleva.onboarding.mandate.application

import ee.tuleva.onboarding.time.TestClockHolder

import static ee.tuleva.onboarding.applicationtype.ApplicationType.*
import static ee.tuleva.onboarding.mandate.application.ApplicationStatus.COMPLETE
import static ee.tuleva.onboarding.mandate.application.ApplicationStatus.PENDING
import static ee.tuleva.onboarding.time.TestClockHolder.*

class ApplicationSnapshotFixture {

  static ApplicationSnapshot sampleTransferApplicationDto() {
    return ApplicationSnapshot.builder()
        .date(now)
        .type(TRANSFER)
        .status(PENDING)
        .id(123L)
        .sourceFundIsin("source")
        .fundTransferExchanges([new ApplicationSnapshot.FundTransfer("target", null, 1.0)])
        .build()
  }

  static ApplicationSnapshot samplePikTransferApplicationDto() {
    return ApplicationSnapshot.builder()
        .date(now)
        .type(TRANSFER)
        .status(PENDING)
        .id(123L)
        .sourceFundIsin("source")
        .fundTransferExchanges([
            new ApplicationSnapshot.FundTransfer(null, "EE471000001020145685", 1.0)
        ])
        .build()
  }

  static ApplicationSnapshot sampleWithdrawalApplicationDto() {
    return ApplicationSnapshot.builder()
        .date(now)
        .type(WITHDRAWAL)
        .status(PENDING)
        .id(123L)
        .bankAccount("IBAN")
        .build()
  }

  static ApplicationSnapshot samplePendingPaymentRateApplicationDto() {
    return ApplicationSnapshot.builder()
        .date(now)
        .type(PAYMENT_RATE)
        .status(PENDING)
        .id(123L)
        .paymentRate(BigDecimal.valueOf(6))
        .build()
  }

  static ApplicationSnapshot sampleFundPensionOpeningApplicationDto() {
    return ApplicationSnapshot.builder()
        .date(now)
        .type(FUND_PENSION_OPENING)
        .bankAccount("EE_TEST_IBAN")
        .fundPensionDetails(new ApplicationSnapshot.FundPensionDetails(20, 12))
        .status(PENDING)
        .id(123L)
        .build()
  }

  static ApplicationSnapshot sampleThirdPillarFundPensionOpeningApplicationDto() {
    return ApplicationSnapshot.builder()
        .date(now)
        .type(FUND_PENSION_OPENING_THIRD_PILLAR)
        .bankAccount("EE_TEST_IBAN")
        .fundPensionDetails(new ApplicationSnapshot.FundPensionDetails(20, 12))
        .status(PENDING)
        .id(123L)
        .build()
  }

  static ApplicationSnapshot samplePartialWithdrawalApplicationDto() {
    return ApplicationSnapshot.builder()
        .date(now)
        .type(PARTIAL_WITHDRAWAL)
        .bankAccount("EE_TEST_IBAN")
        .status(PENDING)
        .id(123L)
        .build()
  }

  static ApplicationSnapshot sampleThirdPillarWithdrawalApplicationDto() {
    return ApplicationSnapshot.builder()
        .date(now)
        .type(WITHDRAWAL_THIRD_PILLAR)
        .bankAccount("EE_TEST_IBAN")
        .status(PENDING)
        .id(123L)
        .build()
  }

  static ApplicationSnapshot sampleCompletedPaymentRateApplicationDto() {
    return ApplicationSnapshot.builder()
        .date(now)
        .type(PAYMENT_RATE)
        .status(COMPLETE)
        .id(123L)
        .paymentRate(BigDecimal.valueOf(4))
        .build()
  }

  static ApplicationSnapshot sampleEarlyWithdrawalApplicationDto() {
    return ApplicationSnapshot.builder()
        .date(now)
        .type(EARLY_WITHDRAWAL)
        .status(PENDING)
        .id(123L)
        .bankAccount("IBAN")
        .build()
  }
}
