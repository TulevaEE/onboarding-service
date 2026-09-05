package ee.tuleva.onboarding.auth.smartid;

public record RememberedSmartIdAccountResponse(String firstName, String lastName) {

  static RememberedSmartIdAccountResponse from(RememberedSmartIdAccount account) {
    return new RememberedSmartIdAccountResponse(account.firstName(), account.lastName());
  }
}
