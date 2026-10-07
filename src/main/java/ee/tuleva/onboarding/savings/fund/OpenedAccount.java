package ee.tuleva.onboarding.savings.fund;

import ee.tuleva.onboarding.auth.principal.Person;
import org.jspecify.annotations.Nullable;

record OpenedAccount(
    String code,
    String firstName,
    String lastName,
    @Nullable String email,
    boolean prefersEnglish,
    boolean represented,
    boolean paid)
    implements Person {

  @Override
  public String getPersonalCode() {
    return code;
  }

  @Override
  public String getFirstName() {
    return firstName;
  }

  @Override
  public String getLastName() {
    return lastName;
  }
}
