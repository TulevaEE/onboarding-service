package ee.tuleva.onboarding.epis.mandate.details;

public record BankAccountDetails(BankAccountType type, String accountIban) {
  public enum BankAccountType {
    ESTONIAN,
    // EUROPEAN(E) and OTHER(V) not supported
  }
}
