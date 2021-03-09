package ee.tuleva.onboarding.mandate.application

import ee.tuleva.onboarding.epis.mandate.ApplicationDTO

import static ee.tuleva.onboarding.epis.mandate.ApplicationStatus.PENDING
import static ee.tuleva.onboarding.mandate.application.ApplicationType.TRANSFER
import static java.math.BigDecimal.*

class ApplicationDtoFixture {
    static ApplicationDTO sampleApplicationDto() {
        return ApplicationDTO.builder()
            .type(TRANSFER)
            .status(PENDING)
            .id(123L)
            .currency("EUR")
            .amount(ONE)
            .sourceFundIsin("source")
            .targetFundIsin("target")
            .build()
    }
}
