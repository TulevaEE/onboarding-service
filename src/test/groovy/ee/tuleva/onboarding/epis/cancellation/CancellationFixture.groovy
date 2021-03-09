package ee.tuleva.onboarding.epis.cancellation

import java.time.Instant

import static ee.tuleva.onboarding.mandate.application.ApplicationType.WITHDRAWAL
import static ee.tuleva.onboarding.user.address.AddressFixture.addressFixture

class CancellationFixture {

    static CancellationDto sampleCancellation() {
        return CancellationDto.builder()
            .id(875L)
            .applicationTypeToCancel(WITHDRAWAL)
            .address(addressFixture().build())
            .processId("cancellationProcessId")
            .createdDate(Instant.parse("2021-03-09T10:00:00Z"))
            .build()
    }

}
