package ee.tuleva.onboarding.investment.risk;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record PublishedRiskIndicator(
    TulevaFund fund,
    RiskIndicatorType indicatorType,
    @Nullable Integer publishedClass,
    @Nullable Integer rawLatestClass,
    @Nullable Integer previousPublishedClass,
    @Nullable LocalDate publishedSince,
    int streakReferencePoints,
    int windowReferencePoints,
    int matchingReferencePoints,
    @Nullable BigDecimal latestVolatility,
    RiskIndicatorStatus status) {}
