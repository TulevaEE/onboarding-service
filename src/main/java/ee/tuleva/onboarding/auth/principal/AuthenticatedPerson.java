package ee.tuleva.onboarding.auth.principal;

import ee.tuleva.onboarding.auth.role.Role;
import ee.tuleva.onboarding.user.personalcode.ValidPersonalCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class AuthenticatedPerson implements Person, Serializable {

  @Serial private static final long serialVersionUID = 2461411670790444975L;

  @ValidPersonalCode String personalCode;

  @NotBlank String firstName;

  @NotBlank String lastName;

  Map<String, String> attributes;

  Long userId;

  @NotNull Role role;

  @Override
  public String toString() {
    return personalCode;
  }

  // TODO: refactor this into specific methods e.g. getPhoneNumber() based on claim keys they were
  // inserted with
  public String getAttribute(String attribute) {
    return attributes.get(attribute);
  }

  public static class AuthenticatedPersonBuilder {

    public AuthenticatedPerson build() {
      return new AuthenticatedPerson(
          personalCode,
          firstName,
          lastName,
          attributes != null ? attributes : Map.of(),
          userId,
          role);
    }
  }
}
