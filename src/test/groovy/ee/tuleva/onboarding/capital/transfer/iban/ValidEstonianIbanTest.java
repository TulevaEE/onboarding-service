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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ValidEstonianIbanTest {

  private static Validator validator;

  @BeforeAll
  static void setUp() {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @Data
  @AllArgsConstructor
  static class TestIbanHolder {
    @ValidEstonianIban private String iban;
  }

  @ParameterizedTest
  @ValueSource(strings = {"EE471000001020145685", "EE382200221020145685"})
  @DisplayName("should pass for valid Estonian IBANs")
  void isValid_whenValidEstonianIban_thenPasses(String iban) {
    // given
    TestIbanHolder holder = new TestIbanHolder(iban);

    // when
    Set<ConstraintViolation<TestIbanHolder>> violations = validator.validate(holder);

    // then
    assertThat(violations).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "DE75512108001245126199", // Valid German IBAN
        "FR7630006000011234567890189", // Valid French IBAN
        "EE471000001020145686" // Estonian prefix, but invalid checksum (fails @ValidIban)
      })
  @DisplayName("should fail for non-Estonian or invalid IBANs")
  void isInvalid_whenNotEstonianOrInvalid_thenFails(String iban) {
    // given
    TestIbanHolder holder = new TestIbanHolder(iban);

    // when
    Set<ConstraintViolation<TestIbanHolder>> violations = validator.validate(holder);

    // then
    assertThat(violations).hasSize(1);
  }
}
