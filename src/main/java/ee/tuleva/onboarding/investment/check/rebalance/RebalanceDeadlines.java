package ee.tuleva.onboarding.investment.check.rebalance;

import java.time.LocalDate;

public record RebalanceDeadlines(LocalDate tradesDue, LocalDate inLineDue, LocalDate legal) {}
