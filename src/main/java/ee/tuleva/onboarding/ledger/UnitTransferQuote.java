package ee.tuleva.onboarding.ledger;

import java.math.BigDecimal;

public record UnitTransferQuote(
    BigDecimal fundUnits,
    BigDecimal giverUnitsAfter,
    BigDecimal receiverUnitsAfter,
    BigDecimal giverPaidIn,
    BigDecimal giverUnitsOwned) {}
