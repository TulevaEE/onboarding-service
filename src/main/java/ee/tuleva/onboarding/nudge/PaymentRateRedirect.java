package ee.tuleva.onboarding.nudge;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

@JsonInclude(NON_NULL)
public record PaymentRateRedirect(
    boolean redirect, @Nullable ExperimentArm arm, @Nullable Integer seasonYear) {

  public static PaymentRateRedirect no() {
    return new PaymentRateRedirect(false, null, null);
  }

  public static PaymentRateRedirect to(ExperimentArm arm, int seasonYear) {
    return new PaymentRateRedirect(true, arm, seasonYear);
  }
}
