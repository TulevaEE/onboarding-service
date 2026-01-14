package ee.tuleva.onboarding.investment.calculation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Builder;

@Builder
public record PositionCalculation(
    String isin,
    TulevaFund fund,
    LocalDate date,
    BigDecimal quantity,
    BigDecimal eodhdPrice,
    BigDecimal yahooPrice,
    BigDecimal usedPrice,
    PriceSource priceSource,
    BigDecimal calculatedMarketValue,
    ValidationStatus validationStatus,
    BigDecimal priceDiscrepancyPercent,
    LocalDate priceDate,
    Instant createdAt) {}
