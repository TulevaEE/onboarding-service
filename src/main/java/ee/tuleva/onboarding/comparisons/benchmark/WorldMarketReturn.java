package ee.tuleva.onboarding.comparisons.benchmark;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WorldMarketReturn(
    int years,
    BigDecimal annualizedReturn,
    LocalDate fromDate,
    LocalDate toDate,
    boolean composite) {}
