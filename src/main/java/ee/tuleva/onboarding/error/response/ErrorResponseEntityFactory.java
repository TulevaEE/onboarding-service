package ee.tuleva.onboarding.error.response;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ErrorResponseEntityFactory {

  private final Converter<Errors, ErrorsResponse> inputErrorsConverter;

  public ResponseEntity<ErrorsResponse> fromErrors(Errors errors) {
    log.info("Create ErrorsResponse from Errors: {}", errors);
    return new ResponseEntity<>(inputErrorsConverter.convert(errors), BAD_REQUEST);
  }
}
