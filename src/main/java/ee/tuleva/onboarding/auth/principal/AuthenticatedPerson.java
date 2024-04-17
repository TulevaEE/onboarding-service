package ee.tuleva.onboarding.auth.principal;

import ee.tuleva.onboarding.user.personalcode.ValidPersonalCode;
import jakarta.validation.constraints.NotBlank;
import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import lombok.*;

@Builder
@Value
public class AuthenticatedPerson implements Person, Serializable {

  @Serial private static final long serialVersionUID = 2461411670790444975L;

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
