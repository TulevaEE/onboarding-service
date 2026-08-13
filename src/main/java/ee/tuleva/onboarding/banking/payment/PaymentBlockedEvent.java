package ee.tuleva.onboarding.banking.payment;

import java.util.List;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record PaymentBlockedEvent(
    PaymentRequest paymentRequest, List<PaymentIntegrityViolation> violations) {}
