package ee.tuleva.onboarding.savings.fund.reminder;

import ee.tuleva.onboarding.auth.principal.Person;
import java.util.Locale;

record FirstPaymentReminder(
    String accountCode,
    String recipientFirstName,
    String recipientLastName,
    String recipientEmail,
    Locale locale)
    implements Person {

  @Override
  public String getPersonalCode() {
    return accountCode;
  }

  @Override
  public String getFirstName() {
    return recipientFirstName;
  }

  @Override
  public String getLastName() {
    return recipientLastName;
  }
}
