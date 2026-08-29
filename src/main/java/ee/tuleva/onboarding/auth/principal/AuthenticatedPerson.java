package ee.tuleva.onboarding.auth.principal;

import static ee.tuleva.onboarding.auth.role.RoleType.LEGAL_ENTITY;
import static ee.tuleva.onboarding.auth.role.RoleType.PERSON;
import static java.util.Objects.requireNonNull;

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
import org.jspecify.annotations.Nullable;

@Builder
@Value
public class AuthenticatedPerson implements Person, Serializable {

  @Serial private static final long serialVersionUID = 2461411670790444975L;

  @ValidPersonalCode String personalCode;

  @NotBlank String firstName;

  @NotBlank String lastName;

  @Builder.Default Map<String, String> attributes = Map.of();

  @Nullable Long userId;

  @NotNull @Nullable Role role;

  @Override
  public String toString() {
    if (role != null && !role.code().equals(personalCode)) {
      return personalCode + " as " + role.code();
    }
    return personalCode;
  }

  @JsonIgnore
  public RoleType getRoleType() {
    return requireNonNull(role, "Role missing: personalCode=" + personalCode).type();
  }

  @JsonIgnore
  public String getRoleCode() {
    return requireNonNull(role, "Role missing: personalCode=" + personalCode).code();
  }

  @JsonIgnore
  public boolean isLegalEntity() {
    return role != null && role.type() == LEGAL_ENTITY;
  }

  @JsonIgnore
  public PartyId toPartyId() {
    return PartyId.from(requireNonNull(role, "Role missing: personalCode=" + personalCode));
  }

  @JsonIgnore
  @Override
  public String getRepresentedPersonalCode() {
    if (role == null || role.type() != PERSON) {
      return personalCode;
    }
    return role.code();
  }

  @JsonIgnore
  public boolean isActingAsSelf() {
    return role == null || role.code().equals(personalCode);
  }

  @JsonIgnore
  public Long getUserIdOrThrow() {
    return requireNonNull(userId, "User id missing: personalCode=" + personalCode);
  }

  public @Nullable String getAttribute(String attribute) {
    return attributes.get(attribute);
  }
}
