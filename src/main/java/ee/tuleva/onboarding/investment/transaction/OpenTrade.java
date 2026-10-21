package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record OpenTrade(
    TulevaFund fund,
    TransactionType side,
    BigDecimal total,
    @Nullable String reference,
    @Nullable String isin,
    @Nullable LocalDate settlementDate,
    @Nullable BigDecimal quantity,
    @Nullable Instant tradeDate) {}
