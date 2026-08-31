package ee.tuleva.onboarding.payment;

@FunctionalInterface
public interface SavingsPayments {

  boolean recordIncoming(IncomingSavingsPayment payment);
}
