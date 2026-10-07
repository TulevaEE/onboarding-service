package ee.tuleva.onboarding.savings.fund;

import ee.tuleva.onboarding.auth.principal.Person;

record OpenedChildAccount(String childCode, String firstName, String lastName, boolean paid)
    implements Person {

  @Override
  public String getPersonalCode() {
    return childCode;
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
