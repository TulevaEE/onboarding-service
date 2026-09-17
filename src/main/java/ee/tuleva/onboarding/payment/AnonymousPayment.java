package ee.tuleva.onboarding.payment;

/**
 * A payment started by somebody who never logged in, together with the description the money will
 * carry.
 *
 * <p>The caller needs the description because it is the only thing that comes back on the payment,
 * whether it arrives through the provider's callback or later from a bank statement. Anything the
 * caller wants to remember about this payment has to hang off it.
 */
public record AnonymousPayment(PaymentLink link, String description) {}
