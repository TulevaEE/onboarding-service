package ee.tuleva.onboarding.banking;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

public record StatementDebit(
    @Nullable String entryId,
    BigDecimal amount,
    String beneficiaryIban,
    @Nullable String endToEndId) {}
