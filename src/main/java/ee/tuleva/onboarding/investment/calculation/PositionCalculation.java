package ee.tuleva.onboarding.investment.calculation;

import ee.tuleva.onboarding.fund.TulevaFund;
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
    BigDecimal usedPrice,
    PriceSource priceSource,
    BigDecimal calculatedMarketValue,
    ValidationStatus validationStatus,
    LocalDate priceDate,
    Instant createdAt) {}
