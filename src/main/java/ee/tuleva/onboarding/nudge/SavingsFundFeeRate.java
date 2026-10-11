package ee.tuleva.onboarding.nudge;

import java.math.BigDecimal;

@FunctionalInterface
public interface SavingsFundFeeRate {

  BigDecimal ongoingChargesPercent();
}
