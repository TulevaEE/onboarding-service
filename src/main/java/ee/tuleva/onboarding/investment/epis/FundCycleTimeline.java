package ee.tuleva.onboarding.investment.epis;

import java.time.LocalDate;

public record FundCycleTimeline(
    LocalDate dActiveDate, LocalDate sellByDate, boolean dActive, boolean sellByReached) {}
