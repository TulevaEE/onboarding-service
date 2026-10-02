package ee.tuleva.onboarding.auth.smartid;

import static ee.tuleva.onboarding.auth.principal.Names.formatted;

public record RememberedSmartIdAccountResponse(String firstName, String lastName) {

  static RememberedSmartIdAccountResponse from(RememberedSmartIdAccount account) {
    return new RememberedSmartIdAccountResponse(
        formatted(account.firstName()), formatted(account.lastName()));
  }
}
