package ee.tuleva.onboarding.investment.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PortfolioCostBasisSnapshot(
    String instrumentIsin,
    BigDecimal quantity,
    BigDecimal avgUnitCost,
    BigDecimal totalCost,
    LocalDate asOfDate) {}
