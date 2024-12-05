package ee.tuleva.onboarding.mandate.application

import ee.tuleva.onboarding.time.TestClockHolder
import ee.tuleva.onboarding.epis.mandate.ApplicationDTO
import ee.tuleva.onboarding.epis.mandate.ApplicationDTO.FundPensionDetails
import ee.tuleva.onboarding.epis.mandate.MandateDto

import static ee.tuleva.onboarding.epis.mandate.ApplicationStatus.COMPLETE
import static ee.tuleva.onboarding.epis.mandate.ApplicationStatus.PENDING
import static ee.tuleva.onboarding.mandate.application.ApplicationType.*

class ApplicationDtoFixture {

  static ApplicationDTO sampleTransferApplicationDto() {
    return ApplicationDTO.builder()
        .date(TestClockHolder.now)
        .type(TRANSFER)
        .status(PENDING)
        .id(123L)
        .currency("EUR")
        .sourceFundIsin("source")
        .fundTransferExchanges([
            new MandateDto.MandateFundsTransferExchangeDTO(
                processId: "processId",
                amount: 1.0,
                sourceFundIsin: "source",
                targetFundIsin: "target",
                targetPik: null
            )
        ])
        .build()
  }

  static ApplicationDTO samplePikTransferApplicationDto() {
    return ApplicationDTO.builder()
        .date(TestClockHolder.now)
        .type(TRANSFER)
        .status(PENDING)
        .id(123L)
        .currency("EUR")
        .sourceFundIsin("source")
        .fundTransferExchanges([
            new MandateDto.MandateFundsTransferExchangeDTO(
                processId: "processId",
                amount: 1.0,
                sourceFundIsin: "source",
                targetFundIsin: null,
                targetPik: "EE801281685311741971"
            )
        ])
        .build()
  }

  static ApplicationDTO sampleWithdrawalApplicationDto() {
    return ApplicationDTO.builder()
        .date(TestClockHolder.now)
        .type(WITHDRAWAL)
        .status(PENDING)
        .id(123L)
        .bankAccount("IBAN")
        .build()
  }

  static ApplicationDTO samplePendingPaymentRateApplicationDto() {
    return ApplicationDTO.builder()
        .date(TestClockHolder.now)
        .type(PAYMENT_RATE)
        .status(PENDING)
        .id(123L)
        .paymentRate(BigDecimal.valueOf(6))
        .build()
  }

  static ApplicationDTO sampleFundPensionOpeningApplicationDto() {
    return ApplicationDTO.builder()
        .date(TestClockHolder.now)
        .type(FUND_PENSION_OPENING)
        .bankAccount("EE_TEST_IBAN")
        .fundPensionDetails(new FundPensionDetails(20, 12))
        .status(PENDING)
        .id(123L)
        .build()
  }

  static ApplicationDTO sampleThirdPillarFundPensionOpeningApplicationDto() {
    return ApplicationDTO.builder()
        .date(TestClockHolder.now)
        .type(FUND_PENSION_OPENING_THIRD_PILLAR)
        .bankAccount("EE_TEST_IBAN")
        .fundPensionDetails(new FundPensionDetails(20, 12))
        .status(PENDING)
        .id(123L)
        .build()
  }

  static ApplicationDTO samplePartialWithdrawalApplicationDto() {
    return ApplicationDTO.builder()
        .date(TestClockHolder.now)
        .type(PARTIAL_WITHDRAWAL)
        .bankAccount("EE_TEST_IBAN")
        .status(PENDING)
        .id(123L)
        .build()
  }

  static ApplicationDTO sampleThirdPillarWithdrawalApplicationDto() {
    return ApplicationDTO.builder()
        .date(TestClockHolder.now)
        .type(WITHDRAWAL_THIRD_PILLAR)
        .bankAccount("EE_TEST_IBAN")
        .status(PENDING)
        .id(123L)
        .build()
  }

  static ApplicationDTO sampleCompletedPaymentRateApplicationDto() {
    return ApplicationDTO.builder()
        .date(TestClockHolder.now)
        .type(PAYMENT_RATE)
        .status(COMPLETE)
        .id(123L)
        .paymentRate(BigDecimal.valueOf(4))
        .build()
  }

  static ApplicationDTO sampleEarlyWithdrawalApplicationDto() {
    return ApplicationDTO.builder()
        .date(TestClockHolder.now)
        .type(EARLY_WITHDRAWAL)
        .status(PENDING)
        .id(123L)
        .bankAccount("IBAN")
        .build()
  }
}
