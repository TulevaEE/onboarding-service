package ee.tuleva.onboarding.mandate.application

import ee.tuleva.onboarding.epis.mandate.ApplicationDTO
import spock.lang.Specification

import static ee.tuleva.onboarding.epis.mandate.ApplicationStatus.PENDING
import static ee.tuleva.onboarding.mandate.application.ApplicationDtoFixture.sampleApplicationDto
import static ee.tuleva.onboarding.mandate.application.ApplicationType.TRANSFER
import static java.math.BigDecimal.ONE

class ApplicationConverterSpec extends Specification {

    ApplicationConverter converter = new ApplicationConverter()

    def "converts ApplicationDTO to application"() {
        given:
        ApplicationDTO applicationDTO = sampleApplicationDto()

        when:
        Application application = converter.convert(applicationDTO)

        then:
        application.id == 123L
        application.type == TRANSFER
        application.status == PENDING
        application.details == TransferApplicationDetails.builder()
            .amount(ONE)
            .currency("EUR")
            .sourceFundIsin("source")
            .targetFundIsin("target")
            .build()
    }
}
