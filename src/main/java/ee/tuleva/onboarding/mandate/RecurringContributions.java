package ee.tuleva.onboarding.mandate;

@FunctionalInterface
public interface RecurringContributions {

  RecurringPayments recurringPaymentsOf(String personalCode);
}
