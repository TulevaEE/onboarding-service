package ee.tuleva.onboarding.mandate.application

import ee.tuleva.onboarding.epis.mandate.ApplicationStatus

import static java.math.BigDecimal.ONE

class ApplicationFixture {
  static Application.ApplicationBuilder sampleApplication() {
    return Application.builder().type(ApplicationType.TRANSFER)
  }

  static Application.ApplicationBuilder transferApplication() {
    return sampleApplication()
      .type(ApplicationType.TRANSFER)
      .status(ApplicationStatus.PENDING)
      .id(123L)
      .details(transferApplicationDetails().build())
  }

  static Application.ApplicationBuilder withdrawalApplication() {
    return sampleApplication()
      .type(ApplicationType.WITHDRAWAL)
      .status(ApplicationStatus.PENDING)
      .id(123L)
      .details(withdrawalApplicationDetails().build())
  }

  static TransferApplicationDetails.TransferApplicationDetailsBuilder transferApplicationDetails() {
    return TransferApplicationDetails.builder()
      .sourceFundIsin("source")
      .targetFundIsin("target")
      .currency("EUR")
      .amount(ONE)
  }

  static WithdrawalApplicationDetails.WithdrawalApplicationDetailsBuilder withdrawalApplicationDetails() {
    return WithdrawalApplicationDetails.builder()
      .depositAccountIBAN("IBAN")
  }
}
