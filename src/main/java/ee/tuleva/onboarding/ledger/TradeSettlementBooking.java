package ee.tuleva.onboarding.ledger;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record TradeSettlementBooking(
    @Nullable TulevaFund fund,
    @Nullable UUID externalReference,
    @Nullable String brokerReference,
    @Nullable String isin,
    LocalDate bookingDate,
    @Nullable BigDecimal cashAmount) {}
