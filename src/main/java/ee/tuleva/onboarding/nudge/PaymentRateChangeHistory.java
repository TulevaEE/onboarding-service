package ee.tuleva.onboarding.nudge;

import ee.tuleva.onboarding.user.User;
import java.time.Instant;

@FunctionalInterface
public interface PaymentRateChangeHistory {

  boolean hasChangeSince(User user, Instant since);
}
