package ee.tuleva.onboarding.banking.payment;

import org.jspecify.annotations.NullMarked;

@NullMarked
public record PaymentMisroutedEvent(PaymentRequest paymentRequest) {}
