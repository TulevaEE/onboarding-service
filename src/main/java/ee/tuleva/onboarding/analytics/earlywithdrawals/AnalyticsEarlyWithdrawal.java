package ee.tuleva.onboarding.analytics.earlywithdrawals;

import ee.tuleva.onboarding.auth.principal.Person;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AnalyticsEarlyWithdrawal(
    String personalCode,
    String firstName,
    String lastName,
    String email,
    String language,
    LocalDate earlyWithdrawalDate,
    String earlyWithdrawalStatus,
    LocalDateTime lastEmailSentDate)
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
