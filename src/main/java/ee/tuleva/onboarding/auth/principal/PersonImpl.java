package ee.tuleva.onboarding.auth.principal;

import ee.tuleva.onboarding.user.personalcode.ValidPersonalCode;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class PersonImpl implements Person {
  @ValidPersonalCode String personalCode;
  @NotBlank String firstName;
  @NotBlank String lastName;
}
