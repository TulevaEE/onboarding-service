package ee.tuleva.onboarding.auth.principal;

import ee.tuleva.onboarding.user.personalcode.ValidPersonalCode;
import javax.validation.constraints.NotBlank;
import lombok.*;

@Value
@NoArgsConstructor(force = true, access = AccessLevel.PRIVATE)
@AllArgsConstructor
@Builder(toBuilder = true)
public class PersonImpl implements Person {
  @ValidPersonalCode String personalCode;
  @NotBlank String firstName;
  @NotBlank String lastName;

  public PersonImpl(Person person) {
    this.personalCode = person.getPersonalCode();
    this.firstName = person.getPersonalCode();
    this.lastName = person.getPersonalCode();
  }
}
