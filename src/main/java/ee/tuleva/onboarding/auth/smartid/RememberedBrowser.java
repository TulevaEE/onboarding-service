package ee.tuleva.onboarding.auth.smartid;

import java.time.Instant;

public record RememberedBrowser(
    String personalCode,
    String documentNumber,
    String firstName,
    String lastName,
    Instant verifiedAt) {

  RememberedSmartIdAccount toAccount() {
    return new RememberedSmartIdAccount(personalCode, documentNumber, firstName, lastName);
  }
}
