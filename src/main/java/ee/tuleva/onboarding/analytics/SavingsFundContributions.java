package ee.tuleva.onboarding.analytics;

import java.time.LocalDate;

@FunctionalInterface
public interface SavingsFundContributions {

  int countIssuedPaymentMonthsSince(SaverId saver, LocalDate from);
}
