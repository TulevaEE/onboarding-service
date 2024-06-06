package ee.tuleva.onboarding.epis

import ee.tuleva.onboarding.error.response.ErrorsResponse
import ee.tuleva.onboarding.mandate.processor.MandateProcess
import ee.tuleva.onboarding.mandate.processor.MandateProcessErrorResolver
import spock.lang.Specification

import static ee.tuleva.onboarding.mandate.application.ApplicationType.SELECTION
import static ee.tuleva.onboarding.mandate.application.ApplicationType.TRANSFER

class MandateProcessErrorResolverSpec extends Specification {

    MandateProcessErrorResolver service = new MandateProcessErrorResolver()

    def "getErrors: get errors response"() {
        when:
        ErrorsResponse errors = service.getErrors(sampleErrorProcesses)
        then:
        errors.errors.size() == 4
        errors.errors.get(0).arguments.get(0) == sampleErrorProcesses.get(0).type.toString()
        errors.errors.get(0).arguments.get(0) == sampleErrorProcesses.get(0).type.toString()

        errors.errors.get(0).getCode() == "mandate.processing.error.epis.technical.error"
        errors.errors.get(1).getCode() == "mandate.processing.error.epis.already.active.contributions.fund"
        errors.errors.get(2).getCode() == "mandate.processing.error.epis.unknown"
        errors.errors.get(3).getCode() == "mandate.processing.error.epis.unknown"

        errors.errors.get(0).getMessage() == "Technical error from EPIS"
        errors.errors.get(1).getMessage() == "Already active contributions fund"
        errors.errors.get(2).getMessage() == "Unknown error from EPIS"
        errors.errors.get(3).getMessage() == "Unknown error from EPIS"

    }

    List<MandateProcess> sampleErrorProcesses = [
            MandateProcess.builder()
                    .successful(false)
                    .type(SELECTION)
                    .errorCode(0)
                    .processId("123")
                    .build()
            ,
            MandateProcess.builder()
                    .successful(false)
                    .type(TRANSFER)
                    .errorCode(40551)
                    .processId("123")
                    .build()
            ,
            MandateProcess.builder()
                    .successful(false)
                    .type(TRANSFER)
                    .errorCode(12345)
                    .processId("123")
                    .build()
            ,
            MandateProcess.builder()
                    .successful(false)
                    .type(TRANSFER)
                    .errorCode(null)
                    .processId("123")
                    .build()

    ]
}
