package ee.tuleva.onboarding.auth.principal;

import ee.tuleva.onboarding.user.personalcode.ValidPersonalCode;
import javax.validation.constraints.NotBlank;

public interface Person {

  @ValidPersonalCode
  String getPersonalCode();

  @NotBlank
  String getFirstName();

  @NotBlank
  String getLastName();
}
