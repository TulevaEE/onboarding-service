package ee.tuleva.onboarding.savings.fund.reminder;

import ee.tuleva.onboarding.auth.principal.Person;
import java.util.Locale;

record FirstPaymentReminder(
    String personalCode, String firstName, String lastName, String email, Locale locale)
    implements Person {

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
