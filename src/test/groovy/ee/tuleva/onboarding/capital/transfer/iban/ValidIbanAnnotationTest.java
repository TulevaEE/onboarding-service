package ee.tuleva.onboarding.capital.transfer.iban;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("@ValidIban Annotation")
class ValidIbanAnnotationTest {

  private static Validator validator;

  @BeforeAll
  static void setUp() {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @Data
  @AllArgsConstructor
  static class TestIbanHolder {
    @ValidIban private String iban;
  }

  @Test
  @DisplayName("should pass for a valid IBAN")
  void isValid_whenValidIban_thenPasses() {
    // given
    TestIbanHolder holder = new TestIbanHolder("DE75512108001245126199");

    // when
    Set<ConstraintViolation<TestIbanHolder>> violations = validator.validate(holder);

    // then
    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("should fail for an invalid IBAN")
  void isInvalid_whenInvalidIban_thenFails() {
    // given
    TestIbanHolder holder = new TestIbanHolder("DE75512108001245126190"); // Invalid checksum

    // when
    Set<ConstraintViolation<TestIbanHolder>> violations = validator.validate(holder);

    // then
    assertThat(violations).hasSize(1);
  }
}
