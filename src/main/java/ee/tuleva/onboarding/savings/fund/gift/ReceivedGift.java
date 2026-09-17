package ee.tuleva.onboarding.savings.fund.gift;

import java.math.BigDecimal;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

// Montonio does not always pass the payer on, so giverName can stay null until the bank statement
// arrives hours later.
public record ReceivedGift(
    Instant receivedAt,
    BigDecimal amount,
    @Nullable String giverName,
    @Nullable String message,
    boolean confirmed) {}
