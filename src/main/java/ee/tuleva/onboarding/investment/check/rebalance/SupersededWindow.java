package ee.tuleva.onboarding.investment.check.rebalance;

import java.time.LocalDate;

record SupersededWindow(RebalanceWindow window, LocalDate replacedOn) {}
