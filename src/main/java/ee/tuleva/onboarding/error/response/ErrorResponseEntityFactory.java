package ee.tuleva.onboarding.error.response;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import ee.tuleva.onboarding.error.converter.InputErrorsConverter;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.Errors;

public class ErrorResponseEntityFactory {

  private final Converter<Errors, ErrorsResponse> inputErrorsConverter = new InputErrorsConverter();

  public ResponseEntity<ErrorsResponse> fromErrors(Errors errors) {
    return new ResponseEntity<>(inputErrorsConverter.convert(errors), BAD_REQUEST);
  }
}
