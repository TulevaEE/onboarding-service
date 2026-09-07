package ee.tuleva.onboarding.investment.check.tracking;

import java.math.BigDecimal;

record NavFlowReconciliation(
    BigDecimal openingNetAssets,
    BigDecimal closingNetAssets,
    BigDecimal marketPnl,
    BigDecimal unitsChange,
    BigDecimal unitFlow,
    BigDecimal feeAccrual,
    BigDecimal unexplained,
    boolean securityQuantitiesChanged) {}
