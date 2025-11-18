package ee.tuleva.onboarding.user.address;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Address validation")
class AddressTest {

  private static Validator validator;

  @BeforeAll
  static void setUp() {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @Test
  @DisplayName("should pass validation for valid country code")
  void isValid_whenValidCountryCode_thenPasses() {
    // given
    Address address = Address.builder().countryCode("EE").build();

    // when
    Set<ConstraintViolation<Address>> violations = validator.validate(address);

    // then
    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("should fail validation when country code is null")
  void isInvalid_whenCountryCodeIsNull_thenFails() {
    // given
    Address address = Address.builder().countryCode(null).build();

    // when
    Set<ConstraintViolation<Address>> violations = validator.validate(address);

    // then
    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getMessage()).isEqualTo("must not be blank");
  }

  @Test
  @DisplayName("should fail validation when country code is empty string")
  void isInvalid_whenCountryCodeIsEmpty_thenFails() {
    // given
    Address address = Address.builder().countryCode("").build();

    // when
    Set<ConstraintViolation<Address>> violations = validator.validate(address);

    // then
    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getMessage()).isEqualTo("must not be blank");
  }

  @Test
  @DisplayName("should fail validation when country code is blank (spaces only)")
  void isInvalid_whenCountryCodeIsBlank_thenFails() {
    // given
    Address address = Address.builder().countryCode("   ").build();

    // when
    Set<ConstraintViolation<Address>> violations = validator.validate(address);

    // then
    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getMessage()).isEqualTo("must not be blank");
  }
}
