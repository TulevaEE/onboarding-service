package ee.tuleva.onboarding.mandate.application

import ee.tuleva.onboarding.ClockFixture
import ee.tuleva.onboarding.epis.mandate.ApplicationDTO
import ee.tuleva.onboarding.epis.mandate.MandateDto

import java.time.Instant

import static ee.tuleva.onboarding.epis.mandate.ApplicationStatus.PENDING
import static ee.tuleva.onboarding.mandate.application.ApplicationType.TRANSFER
import static ee.tuleva.onboarding.mandate.application.ApplicationType.WITHDRAWAL

class ApplicationDtoFixture {
  static ApplicationDTO sampleTransferApplicationDto() {
    return ApplicationDTO.builder()
      .date(Instant.now(ClockFixture.clock))
      .type(TRANSFER)
      .status(PENDING)
      .id(123L)
      .currency("EUR")
      .sourceFundIsin("source")
      .fundTransferExchanges([new MandateDto.MandateFundsTransferExchangeDTO("processId", 1.0, "source", "target")])
      .build()
  }

  static ApplicationDTO sampleWithdrawalApplicationDto() {
    return ApplicationDTO.builder()
      .date(Instant.now(ClockFixture.clock))
      .type(WITHDRAWAL)
      .status(PENDING)
      .id(123L)
      .bankAccount("IBAN")
      .build()
  }
}
