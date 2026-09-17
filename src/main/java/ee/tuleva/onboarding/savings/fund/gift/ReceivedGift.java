package ee.tuleva.onboarding.savings.fund.gift;

import java.math.BigDecimal;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * One gift as the parent sees it.
 *
 * @param giverName what the bank said, which is often nothing. Montonio does not always pass the
 *     sender on, and then the name only arrives with the bank statement hours later, so a gift can
 *     legitimately sit here nameless for a while.
 * @param message null for a gift paid by plain bank transfer, where there was no page to write one
 *     on, and for the rare case where two gifts to this child share a payment description and we
 *     would rather show nothing than the wrong words.
 * @param confirmed false while the money is on its way but not yet verified, so the page can say so
 *     instead of implying it has landed.
 */
public record ReceivedGift(
    Instant receivedAt,
    BigDecimal amount,
    @Nullable String giverName,
    @Nullable String message,
    boolean confirmed) {}
