package ee.tuleva.onboarding.mandate.processor

import ee.tuleva.onboarding.error.response.ErrorsResponse
import spock.lang.Specification

import static ee.tuleva.onboarding.applicationtype.ApplicationType.SELECTION
import static ee.tuleva.onboarding.applicationtype.ApplicationType.TRANSFER

class MandateProcessErrorResolverSpec extends Specification {

    MandateProcessErrorResolver service = new MandateProcessErrorResolver()

    def "getErrors: get errors response, dropping successful and unreported-status processes"() {
        when:
        ErrorsResponse errors = service.getErrors(sampleProcesses)
        then:
        errors.errors.size() == 5
        errors.errors.get(0).arguments.get(0) == sampleProcesses.get(0).type.toString()
        errors.errors.get(0).arguments.get(0) == sampleProcesses.get(0).type.toString()

        errors.errors.get(0).getCode() == "mandate.processing.error.epis.technical.error"
        errors.errors.get(1).getCode() == "mandate.processing.error.epis.already.active.contributions.fund"
        errors.errors.get(2).getCode() == "mandate.processing.error.epis.unknown"
        errors.errors.get(3).getCode() == "mandate.processing.error.epis.unknown"
        errors.errors.get(4).getCode() == "mandate.processing.error.epis.contributions.not.stopped.by.application"

        errors.errors.get(0).getMessage() == "Technical error from EPIS"
        errors.errors.get(1).getMessage() == "Already active contributions fund"
        errors.errors.get(2).getMessage() == "Unknown error from EPIS"
        errors.errors.get(3).getMessage() == "Unknown error from EPIS"
        errors.errors.get(4).getMessage() == "Contributions were not stopped by an application"

    }

    def "getErrors: a signed mandate with no process on record was never sent to EPIS"() {
        when:
        ErrorsResponse errors = service.getErrors([])
        then:
        errors == ErrorsResponse.ofSingleError("mandate.processing.error.not.sent", "Mandate was not sent to EPIS")
    }

    List<MandateProcess> sampleProcesses = [
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
            ,
            MandateProcess.builder()
                    .successful(false)
                    .type(TRANSFER)
                    .errorCode(41255)
                    .processId("123")
                    .build()
            ,
            MandateProcess.builder()
                    .successful(true)
                    .type(TRANSFER)
                    .errorCode(40551)
                    .processId("123")
                    .build()
            ,
            MandateProcess.builder()
                    .successful(null)
                    .type(TRANSFER)
                    .errorCode(40551)
                    .processId("123")
                    .build()

    ]
}
