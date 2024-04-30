package ee.tuleva.onboarding.analytics;

import ee.tuleva.onboarding.auth.principal.Person;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AnalyticsLeaver(
    String currentFund,
    String newFund,
    String personalCode,
    String firstName,
    String lastName,
    Double shareAmount,
    Double sharePercentage,
    LocalDate dateCreated,
    Double fundOngoingChargesFigure,
    String fundNameEstonian,
    String email,
    String language,
    Integer age,
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
