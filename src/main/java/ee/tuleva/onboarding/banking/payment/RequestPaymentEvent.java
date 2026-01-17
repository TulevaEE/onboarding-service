package ee.tuleva.onboarding.banking.payment;

import java.util.UUID;

public record RequestPaymentEvent(PaymentRequest paymentRequest, UUID requestId) {}
