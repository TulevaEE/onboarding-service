package ee.tuleva.onboarding.savings.fund;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.auth.principal.PersonImpl;
import org.jspecify.annotations.Nullable;

record OpenedAccount(
    String code,
    String firstName,
    String lastName,
    @Nullable String email,
    boolean prefersEnglish,
    boolean represented,
    boolean paid) {

  Person holder() {
    return new PersonImpl(code, firstName, lastName);
  }
}
