package ee.tuleva.onboarding.savings.fund.reminder;

import ee.tuleva.onboarding.auth.principal.Person;

record ChildOnboardingAbandonmentReminder(
    String parentCode, String parentFirstName, String parentLastName, String parentEmail)
    implements Person {

  @Override
  public String getPersonalCode() {
    return parentCode;
  }

  @Override
  public String getFirstName() {
    return parentFirstName;
  }

  @Override
  public String getLastName() {
    return parentLastName;
  }
}
