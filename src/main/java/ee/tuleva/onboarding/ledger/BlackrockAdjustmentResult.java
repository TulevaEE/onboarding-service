package ee.tuleva.onboarding.ledger;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;

public record BlackrockAdjustmentResult(
    TulevaFund fund,
    LocalDate date,
    BigDecimal previousBalance,
    BigDecimal targetBalance,
    BigDecimal delta,
    boolean transactionCreated) {}
