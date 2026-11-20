package ee.tuleva.onboarding.banking;

import java.math.BigDecimal;
import java.time.Instant;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record FetchedBalance(BigDecimal amount, Instant fetchedAt) {}
