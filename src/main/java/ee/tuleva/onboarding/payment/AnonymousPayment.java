package ee.tuleva.onboarding.payment;

// The description is the only thing that comes back on a payment, whether through the provider's
// callback or later from a bank statement, so anything the caller wants to remember hangs off it.
public record AnonymousPayment(PaymentLink link, String description) {}
