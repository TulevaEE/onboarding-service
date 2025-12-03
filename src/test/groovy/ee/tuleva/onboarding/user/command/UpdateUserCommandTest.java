package ee.tuleva.onboarding.user.command;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.country.Country;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UpdateUserCommand validation")
class UpdateUserCommandTest {

  private static Validator validator;

  @BeforeAll
  static void setUp() {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @Test
  @DisplayName("should pass validation with valid email and address")
  void isValid_whenAllFieldsValid_thenPasses() {
    // given
    UpdateUserCommand command = new UpdateUserCommand();
    command.setEmail("test@example.com");
    command.setPhoneNumber("+3725551234");
    command.setAddress(Country.builder().countryCode("EE").build());

    // when
    Set<ConstraintViolation<UpdateUserCommand>> violations = validator.validate(command);

    // then
    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("should fail validation when email is null")
  void isInvalid_whenEmailIsNull_thenFails() {
    // given
    UpdateUserCommand command = new UpdateUserCommand();
    command.setEmail(null);
    command.setAddress(Country.builder().countryCode("EE").build());

    // when
    Set<ConstraintViolation<UpdateUserCommand>> violations = validator.validate(command);

    // then
    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("email");
  }

  @Test
  @DisplayName("should fail validation when email is invalid")
  void isInvalid_whenEmailIsInvalid_thenFails() {
    // given
    UpdateUserCommand command = new UpdateUserCommand();
    command.setEmail("not-an-email");
    command.setAddress(Country.builder().countryCode("EE").build());

    // when
    Set<ConstraintViolation<UpdateUserCommand>> violations = validator.validate(command);

    // then
    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("email");
  }

  @Test
  @DisplayName("should fail validation when address has empty country code")
  void isInvalid_whenAddressCountryCodeIsEmpty_thenFails() {
    // given
    UpdateUserCommand command = new UpdateUserCommand();
    command.setEmail("test@example.com");
    command.setAddress(Country.builder().countryCode("").build());

    // when
    Set<ConstraintViolation<UpdateUserCommand>> violations = validator.validate(command);

    // then
    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getPropertyPath().toString())
        .isEqualTo("address.countryCode");
    assertThat(violations.iterator().next().getMessage()).isEqualTo("must not be blank");
  }

  @Test
  @DisplayName("should fail validation when address has null country code")
  void isInvalid_whenAddressCountryCodeIsNull_thenFails() {
    // given
    UpdateUserCommand command = new UpdateUserCommand();
    command.setEmail("test@example.com");
    command.setAddress(Country.builder().countryCode(null).build());

    // when
    Set<ConstraintViolation<UpdateUserCommand>> violations = validator.validate(command);

    // then
    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getPropertyPath().toString())
        .isEqualTo("address.countryCode");
    assertThat(violations.iterator().next().getMessage()).isEqualTo("must not be blank");
  }

  @Test
  @DisplayName("should fail validation when address has blank country code")
  void isInvalid_whenAddressCountryCodeIsBlank_thenFails() {
    // given
    UpdateUserCommand command = new UpdateUserCommand();
    command.setEmail("test@example.com");
    command.setAddress(Country.builder().countryCode("   ").build());

    // when
    Set<ConstraintViolation<UpdateUserCommand>> violations = validator.validate(command);

    // then
    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getPropertyPath().toString())
        .isEqualTo("address.countryCode");
    assertThat(violations.iterator().next().getMessage()).isEqualTo("must not be blank");
  }

  @Test
  @DisplayName("should pass validation when address is null")
  void isValid_whenAddressIsNull_thenPasses() {
    // given
    UpdateUserCommand command = new UpdateUserCommand();
    command.setEmail("test@example.com");
    command.setAddress(null);

    // when
    Set<ConstraintViolation<UpdateUserCommand>> violations = validator.validate(command);

    // then
    assertThat(violations).isEmpty();
  }
}
