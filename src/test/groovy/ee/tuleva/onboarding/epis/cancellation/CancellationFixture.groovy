package ee.tuleva.onboarding.epis.cancellation

import ee.tuleva.onboarding.epis.mandate.GenericMandateDto
import ee.tuleva.onboarding.epis.mandate.details.CancellationMandateDetails

import java.time.Instant

import static ee.tuleva.onboarding.mandate.application.ApplicationType.WITHDRAWAL
import static ee.tuleva.onboarding.user.address.AddressFixture.addressFixture

class CancellationFixture {

    static GenericMandateDto<CancellationMandateDetails> sampleCancellation() {
        return  GenericMandateDto.<CancellationMandateDetails>builder()
            .id(875L)
            .createdDate(Instant.parse("2021-03-09T10:00:00Z"))
            .address(addressFixture().build())
            .email("email@override.ee")
            .phoneNumber("+37288888888")
            .details(new CancellationMandateDetails(WITHDRAWAL))
            .build()
    }

}
