package ee.tuleva.onboarding.error.response;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Slf4j
@Component
@RequiredArgsConstructor
public class ErrorResponseEntityFactory {

    private final InputErrorsConverter inputErrorsConverter;

    public ResponseEntity<ErrorsResponse> fromErrors(Errors errors) {
        log.info("Create ErrorsResponse from Errors: {}", errors);
        return new ResponseEntity<>(inputErrorsConverter.convert(errors), BAD_REQUEST);
    }

}
