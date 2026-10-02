package ee.tuleva.onboarding.signature;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class StartIdCardSignCommandTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void acceptsTheHashFunctionsACardReports() {
    var command =
        new StartIdCardSignCommand(
            "certificate", List.of("SHA-224", "SHA-256", "SHA-384", "SHA-512", "SHA3-512"));

    assertThat(validator.validate(command)).isEmpty();
  }

  @Test
  void rejectsMoreHashFunctionsThanAnyCardReports() {
    var command = new StartIdCardSignCommand("certificate", Collections.nCopies(11, "SHA-256"));

    assertThat(validator.validate(command)).hasSize(1);
  }

  @Test
  void rejectsAHashFunctionNameLongerThanAnyHashFunctionName() {
    var command = new StartIdCardSignCommand("certificate", List.of("SHA-256".repeat(10)));

    assertThat(validator.validate(command)).hasSize(1);
  }
}
