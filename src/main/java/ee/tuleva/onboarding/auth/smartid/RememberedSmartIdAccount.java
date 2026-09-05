package ee.tuleva.onboarding.auth.smartid;

public record RememberedSmartIdAccount(
    String personalCode, String documentNumber, String firstName, String lastName) {}
