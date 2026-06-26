package ee.tuleva.onboarding.investment.event;

import java.time.LocalDate;

public record RunTdAttributionRequested(
    String fundCode, LocalDate periodStart, LocalDate periodEnd, String periodType) {}
