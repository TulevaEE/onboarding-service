package ee.tuleva.onboarding.auth.principal;

import ee.tuleva.onboarding.user.personalcode.ValidPersonalCode;
import java.util.HashMap;
import java.util.Map;
import javax.validation.constraints.NotBlank;
import lombok.*;

@Builder
@Value
public class AuthenticatedPerson implements Person {

  @ValidPersonalCode String personalCode;

  @NotBlank String firstName;

  @NotBlank String lastName;

  @Builder.Default Map<String, String> attributes = new HashMap<>();

  Long userId;

  @Override
  public String toString() {
    return personalCode;
  }

  public String getAttribute(String attribute) {
    return attributes.get(attribute);
  }
}
