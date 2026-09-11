package ee.tuleva.onboarding.epis.cancellation

import ee.tuleva.onboarding.mandate.GenericMandateSubmission
import ee.tuleva.onboarding.mandate.details.EarlyWithdrawalCancellationMandateDetails
import ee.tuleva.onboarding.pillar.Pillar
import ee.tuleva.onboarding.mandate.details.TransferCancellationMandateDetails
import ee.tuleva.onboarding.mandate.details.WithdrawalCancellationMandateDetails

import java.time.Instant

import static ee.tuleva.onboarding.country.CountryFixture.countryFixture

class CancellationFixture {

    static GenericMandateSubmission<WithdrawalCancellationMandateDetails> sampleWithdrawalCancellation() {
        return  GenericMandateSubmission.<WithdrawalCancellationMandateDetails>builder()
            .id(875L)
            .createdDate(Instant.parse("2021-03-09T10:00:00Z"))
            .address(countryFixture().build())
            .email("email@override.ee")
            .phoneNumber("+37288888888")
            .details(new WithdrawalCancellationMandateDetails())
            .build()
    }

  static GenericMandateSubmission<EarlyWithdrawalCancellationMandateDetails> sampleEarlyWithdrawalCancellation() {
    return  GenericMandateSubmission.<EarlyWithdrawalCancellationMandateDetails>builder()
        .id(875L)
        .createdDate(Instant.parse("2021-03-09T10:00:00Z"))
        .address(countryFixture().build())
        .email("email@override.ee")
        .phoneNumber("+37288888888")
        .details(new EarlyWithdrawalCancellationMandateDetails())
        .build()
  }

  static GenericMandateSubmission<TransferCancellationMandateDetails> sampleTransferCancellation(String isinToCancel, Pillar pillar) {
    return GenericMandateSubmission.<TransferCancellationMandateDetails>builder()
        .id(875L)
        .createdDate(Instant.parse("2021-03-09T10:00:00Z"))
        .address(countryFixture().build())
        .email("email@override.ee")
        .phoneNumber("+37288888888")
        .details(new TransferCancellationMandateDetails(isinToCancel, pillar))
        .build()
  }

}
