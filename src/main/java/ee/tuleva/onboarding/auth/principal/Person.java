package ee.tuleva.onboarding.auth.principal;

import ee.tuleva.onboarding.user.personalcode.ValidPersonalCode;
import jakarta.validation.constraints.NotBlank;

public interface Person {

  @ValidPersonalCode
  String getPersonalCode();

  @NotBlank
  String getFirstName();

  @NotBlank
  String getLastName();
}
