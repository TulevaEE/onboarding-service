package ee.tuleva.onboarding.auth.principal;

import static org.apache.commons.lang3.text.WordUtils.capitalizeFully;

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

  static String capitalize(String name) {
    return capitalizeFully(name, ' ', '-');
  }
}
