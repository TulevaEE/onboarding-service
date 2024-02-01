package ee.tuleva.onboarding.auth.principal;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ee.tuleva.onboarding.user.personalcode.ValidPersonalCode;
import jakarta.validation.constraints.NotBlank;

public interface Person {

  @ValidPersonalCode
  String getPersonalCode();

  @NotBlank
  String getFirstName();

  @NotBlank
  String getLastName();

  @JsonIgnore
  default String getFullName() {
    return getFirstName() + " " + getLastName();
  }
}
