package ee.tuleva.onboarding.mandate.processor;

import ee.tuleva.onboarding.error.response.ErrorResponse;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class MandateProcessErrorResolver {


    public ErrorsResponse getErrors(List<MandateProcess> processes) {

        return new ErrorsResponse(
                processes.stream()
                        .filter(process -> !process.isSuccessful().orElse(true))
                        .map(process -> resolveErrorResponseFromProcess(process))
                        .collect(Collectors.toList())
        );
    }


    private ErrorResponse resolveErrorResponseFromProcess(MandateProcess process) {
        return ErrorResponse.builder()
                .arguments(Arrays.asList(process.getType().toString()))
                .code(resolveErrorCodeFromEpisCode(process.getErrorCode()))
                .message(resolveErrorMessageFromEpisCode(process.getErrorCode()))
                .build();
    }

    private String resolveErrorCodeFromEpisCode(Optional<Integer> errorCode) {
        String UNKNOWN_ERROR_CODE = "mandate.processing.error.epis.unknown";

        return errorCode.map(code -> {
            if(code == 0) {
                return "mandate.processing.error.epis.technical.error";
            } else if(code == 40551) {
                return "mandate.processing.error.epis.already.active.contributions.fund";
            } else {
                log.warn("Couldn't resolve EPIS error code {}", errorCode.toString());
                return UNKNOWN_ERROR_CODE;
            }
        }).orElse(UNKNOWN_ERROR_CODE);
    }

    private String resolveErrorMessageFromEpisCode(Optional<Integer> errorCode) {
        String UNKNOWN_ERROR_MESSAGE = "Unknown error from EPIS";

        return errorCode.map(code -> {
            if(code == 0) {
                return "Technical error from EPIS";
            } else if(code == 40551) {
                return "Already active contributions fund";
            } else {
                return UNKNOWN_ERROR_MESSAGE;
            }
        }).orElse(UNKNOWN_ERROR_MESSAGE);
    }

}
