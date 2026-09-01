package ee.tuleva.onboarding.analytics;

import ee.tuleva.onboarding.auth.principal.Person;

public record RecentThirdPillarCustomer(
    String personalCode, String firstName, String lastName, String country) implements Person {

  @Override
  public String getPersonalCode() {
    return personalCode;
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
