package ee.tuleva.onboarding.analytics;

import java.time.LocalDate;

@FunctionalInterface
public interface SavingsFundContributions {

  int countStandingOrderMonthsSince(SaverId saver, LocalDate from);
}
