package ee.tuleva.onboarding.auth.role;

import static ee.tuleva.onboarding.auth.role.RoleType.LEGAL_ENTITY;
import static ee.tuleva.onboarding.auth.role.RoleType.PERSON;

import ee.tuleva.onboarding.user.personalcode.PersonalCodeValidator;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.regex.Pattern;

public record Role(@NotNull RoleType type, @NotNull String code, String name)
    implements Serializable {

  private static final Pattern REGISTRY_CODE_PATTERN = Pattern.compile("\\d{8}");
  private static final PersonalCodeValidator PERSONAL_CODE_VALIDATOR = new PersonalCodeValidator();

  public Role {
    if (code == null) {
      throw new IllegalArgumentException("Role code must not be null");
    }
    if (type == PERSON && !PERSONAL_CODE_VALIDATOR.isValid(code)) {
      throw new IllegalArgumentException("Invalid personal code: code=" + code);
    }
    if (type == LEGAL_ENTITY && !REGISTRY_CODE_PATTERN.matcher(code).matches()) {
      throw new IllegalArgumentException("Invalid registry code: code=" + code);
    }
  }
}
