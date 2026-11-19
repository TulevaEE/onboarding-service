package ee.tuleva.onboarding.investment.transaction.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;

record CostBasisOversell(
    String fundIsin,
    String instrumentIsin,
    LocalDate asOfDate,
    BigDecimal heldQuantity,
    BigDecimal sellQuantity) {}
