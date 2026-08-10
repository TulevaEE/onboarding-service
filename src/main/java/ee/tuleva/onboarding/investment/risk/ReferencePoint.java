package ee.tuleva.onboarding.investment.risk;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.jspecify.annotations.Nullable;

record ReferencePoint(
    LocalDate date,
    @Nullable Integer riskClass,
    int observationCount,
    BigDecimal volatility,
    Map<String, Object> metrics) {}
