package ee.tuleva.onboarding.mandate.application

import ee.tuleva.onboarding.ClockFixture
import ee.tuleva.onboarding.epis.mandate.ApplicationStatus
import ee.tuleva.onboarding.fund.response.FundDto

import java.time.Instant

import static ee.tuleva.onboarding.mandate.MandateFixture.sampleFunds

class ApplicationFixture {
  static Application.ApplicationBuilder sampleApplication() {
    return Application.builder().type(ApplicationType.TRANSFER)
  }

  static Application.ApplicationBuilder transferApplication() {
    return sampleApplication()
      .creationTime(Instant.now(ClockFixture.clock))
      .type(ApplicationType.TRANSFER)
      .status(ApplicationStatus.PENDING)
      .id(123L)
      .details(transferApplicationDetails().build())
  }

  static Application.ApplicationBuilder withdrawalApplication() {
    return sampleApplication()
      .creationTime(Instant.now(ClockFixture.clock))
      .type(ApplicationType.WITHDRAWAL)
      .status(ApplicationStatus.PENDING)
      .id(123L)
      .details(withdrawalApplicationDetails().build())
  }

  static TransferApplicationDetails.TransferApplicationDetailsBuilder transferApplicationDetails() {
    return TransferApplicationDetails.builder()
      .sourceFund(new FundDto(sampleFunds().first(), 'en'))
      .exchange(TransferApplicationDetails.Exchange.builder()
        .amount(BigDecimal.ONE)
        .targetFund(new FundDto(sampleFunds().drop(1).first(), 'en'))
        .build())
      .exchange(TransferApplicationDetails.Exchange.builder()
        .amount(BigDecimal.ONE)
        .targetFund(new FundDto(sampleFunds().drop(1).first(), 'en'))
        .build())
  }

  static WithdrawalApplicationDetails.WithdrawalApplicationDetailsBuilder withdrawalApplicationDetails() {
    return WithdrawalApplicationDetails.builder()
      .depositAccountIBAN("IBAN")
  }
}
