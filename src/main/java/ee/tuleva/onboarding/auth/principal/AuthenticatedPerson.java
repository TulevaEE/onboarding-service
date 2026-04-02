package ee.tuleva.onboarding.auth.principal;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ee.tuleva.onboarding.auth.role.Role;
import ee.tuleva.onboarding.auth.role.RoleType;
import ee.tuleva.onboarding.party.PartyId;
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
    if (role != null && !role.code().equals(personalCode)) {
      return personalCode + " as " + role.code();
    }
    return personalCode;
  }

  @JsonIgnore
  public RoleType getRoleType() {
    return role.type();
  }

  @JsonIgnore
  public String getRoleCode() {
    return role.code();
  }

  @JsonIgnore
  public PartyId toPartyId() {
    return PartyId.from(role);
  }

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
