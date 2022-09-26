package ee.tuleva.onboarding.mandate.application

import ee.tuleva.onboarding.time.TestClockHolder
import ee.tuleva.onboarding.fund.ApiFundResponse

import java.time.Instant

import static ee.tuleva.onboarding.epis.mandate.ApplicationStatus.*
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleFunds
import static ee.tuleva.onboarding.mandate.application.Application.*
import static ee.tuleva.onboarding.mandate.application.TransferApplicationDetails.*

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

  static TransferApplicationDetailsBuilder transferApplicationDetails() {
    def sourceFund = new ApiFundResponse(sampleFunds().first(), 'en')
    def targetFund = new ApiFundResponse(sampleFunds().drop(1).first(), 'en')

    return TransferApplicationDetails.builder()
        .sourceFund(sourceFund)
        .exchange(Exchange.builder()
            .sourceFund(sourceFund)
            .targetFund(targetFund)
            .amount(1.0)
            .build()
        )
  }

  static WithdrawalApplicationDetails.WithdrawalApplicationDetailsBuilder withdrawalApplicationDetails() {
    return WithdrawalApplicationDetails.builder()
        .depositAccountIBAN("IBAN")
  }
}
