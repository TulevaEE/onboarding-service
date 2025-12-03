package ee.tuleva.onboarding.epis.cancellation

import ee.tuleva.onboarding.epis.mandate.GenericMandateDto
import ee.tuleva.onboarding.epis.mandate.details.EarlyWithdrawalCancellationMandateDetails
import ee.tuleva.onboarding.pillar.Pillar
import ee.tuleva.onboarding.epis.mandate.details.TransferCancellationMandateDetails
import ee.tuleva.onboarding.epis.mandate.details.WithdrawalCancellationMandateDetails

import java.time.Instant

import static ee.tuleva.onboarding.country.CountryFixture.countryFixture

class CancellationFixture {

    static GenericMandateDto<WithdrawalCancellationMandateDetails> sampleWithdrawalCancellation() {
        return  GenericMandateDto.<WithdrawalCancellationMandateDetails>builder()
            .id(875L)
            .createdDate(Instant.parse("2021-03-09T10:00:00Z"))
            .address(countryFixture().build())
            .email("email@override.ee")
            .phoneNumber("+37288888888")
            .details(new WithdrawalCancellationMandateDetails())
            .build()
    }

  static GenericMandateDto<EarlyWithdrawalCancellationMandateDetails> sampleEarlyWithdrawalCancellation() {
    return  GenericMandateDto.<EarlyWithdrawalCancellationMandateDetails>builder()
        .id(875L)
        .createdDate(Instant.parse("2021-03-09T10:00:00Z"))
        .address(countryFixture().build())
        .email("email@override.ee")
        .phoneNumber("+37288888888")
        .details(new EarlyWithdrawalCancellationMandateDetails())
        .build()
  }

  static GenericMandateDto<TransferCancellationMandateDetails> sampleTransferCancellation(String isinToCancel, Pillar pillar) {
    return GenericMandateDto.<TransferCancellationMandateDetails>builder()
        .id(875L)
        .createdDate(Instant.parse("2021-03-09T10:00:00Z"))
        .address(countryFixture().build())
        .email("email@override.ee")
        .phoneNumber("+37288888888")
        .details(new TransferCancellationMandateDetails(isinToCancel, pillar))
        .build()
  }

}
