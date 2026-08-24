package ee.tuleva.onboarding.kyb.survey;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.jspecify.annotations.NullMarked;

@NullMarked
record ValidationError(
    String code, String message, @JsonInclude(NON_EMPTY) List<RelatedPersonData> persons) {

  ValidationError(String code, String message) {
    this(code, message, List.of());
  }
}
