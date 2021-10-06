package ee.tuleva.onboarding.mandate.application

import ee.tuleva.onboarding.ClockFixture
import ee.tuleva.onboarding.epis.mandate.ApplicationDTO
import ee.tuleva.onboarding.epis.mandate.MandateDto

import static ee.tuleva.onboarding.epis.mandate.ApplicationStatus.PENDING
import static ee.tuleva.onboarding.mandate.application.ApplicationType.*

class ApplicationDtoFixture {
  static ApplicationDTO sampleTransferApplicationDto() {
    return ApplicationDTO.builder()
      .date(ClockFixture.now)
      .type(TRANSFER)
      .status(PENDING)
      .id(123L)
      .currency("EUR")
      .sourceFundIsin("source")
      .fundTransferExchanges([
        new MandateDto.MandateFundsTransferExchangeDTO("processId", 1.0, "source", "target", null)
      ])
      .build()
  }

  static ApplicationDTO samplePikTransferApplicationDto() {
    return ApplicationDTO.builder()
      .date(ClockFixture.now)
      .type(TRANSFER)
      .status(PENDING)
      .id(123L)
      .currency("EUR")
      .sourceFundIsin("source")
      .fundTransferExchanges([
        new MandateDto.MandateFundsTransferExchangeDTO("processId", 1.0, "source", null, "EE801281685311741971")
      ])
      .build()
  }

  static ApplicationDTO sampleWithdrawalApplicationDto() {
    return ApplicationDTO.builder()
      .date(ClockFixture.now)
      .type(WITHDRAWAL)
      .status(PENDING)
      .id(123L)
      .bankAccount("IBAN")
      .build()
  }

  static ApplicationDTO sampleEarlyWithdrawalApplicationDto() {
    return ApplicationDTO.builder()
      .date(ClockFixture.now)
      .type(EARLY_WITHDRAWAL)
      .status(PENDING)
      .id(123L)
      .bankAccount("IBAN")
      .build()
  }
}
