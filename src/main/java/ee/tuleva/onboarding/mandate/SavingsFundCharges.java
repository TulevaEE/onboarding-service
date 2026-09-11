package ee.tuleva.onboarding.mandate;

import java.util.Locale;

@FunctionalInterface
public interface SavingsFundCharges {

  String ongoingChargesPercent(Locale locale);
}
