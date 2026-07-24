package ee.tuleva.onboarding.investment.transaction;

import java.math.BigDecimal;

public record LiabilityBreakdown(
    BigDecimal managementFee,
    BigDecimal depotFee,
    BigDecimal pevaRava,
    BigDecimal r16,
    BigDecimal r45Net,
    BigDecimal pendingBuys,
    BigDecimal pendingSells,
    BigDecimal unreconciledBankReceipts,
    BigDecimal fundUnitsReservedValue,
    BigDecimal incomingPaymentsClearing) {}
