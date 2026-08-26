package ee.tuleva.onboarding.analytics.transaction.thirdpillar;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.notification.email.Emailable;
import java.math.BigDecimal;
import java.time.LocalDate;

public record FirstThirdPillarPayment(
    String personalCode,
    String firstName,
    String lastName,
    String email,
    String languagePreference,
    BigDecimal amount,
    LocalDate firstPaymentDate,
    boolean hasTulevaUser,
    boolean suggestSecondPillar,
    boolean suggestPaymentRate,
    boolean suggestMembership,
    boolean leftSecondPillar,
    boolean savesInSavingsFund)
    implements Person, Emailable {

  public boolean suggestSavingsFund() {
    return !suggestSecondPillar && !suggestPaymentRate && !savesInSavingsFund;
  }

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

  @Override
  public String getEmail() {
    return email;
  }

  public String emailLanguage() {
    return "ENG".equalsIgnoreCase(languagePreference) ? "en" : "et";
  }
}
