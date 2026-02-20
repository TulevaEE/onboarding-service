package ee.tuleva.onboarding.auth.principal;

import ee.tuleva.onboarding.user.personalcode.ValidPersonalCode;
import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import lombok.*;

@Value
@NoArgsConstructor(force = true, access = AccessLevel.PRIVATE)
@AllArgsConstructor
@Builder(toBuilder = true)
public class PersonImpl implements Person, Serializable {
  @ValidPersonalCode String personalCode;
  @NotBlank String firstName;
  @NotBlank String lastName;

  public PersonImpl(Person person) {
    this.personalCode = person.getPersonalCode();
    this.firstName = person.getFirstName();
    this.lastName = person.getLastName();
  }
}
