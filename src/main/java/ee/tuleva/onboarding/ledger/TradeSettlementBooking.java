package ee.tuleva.onboarding.ledger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record TradeSettlementBooking(
    @Nullable UUID externalReference,
    @Nullable String brokerReference,
    @Nullable String isin,
    LocalDate bookingDate,
    BigDecimal cashAmount) {}
