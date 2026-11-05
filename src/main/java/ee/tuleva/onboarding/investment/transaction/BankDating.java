package ee.tuleva.onboarding.investment.transaction;

public sealed interface BankDating {

  record Settled() implements BankDating {}

  record AlreadyByTheBank() implements BankDating {}

  record Redated(PreviousSettlement previous) implements BankDating {}
}
