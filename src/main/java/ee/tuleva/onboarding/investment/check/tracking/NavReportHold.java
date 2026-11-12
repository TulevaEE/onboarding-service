package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.check.tracking.NavReportHold.Residual.AT_THRESHOLD;
import static ee.tuleva.onboarding.investment.check.tracking.NavReportHold.Run.COMPLETED;

public final class NavReportHold {

  public enum Run {
    COMPLETED,
    INCOMPLETE_PRICES,
    FAILED
  }

  public enum Residual {
    AT_THRESHOLD,
    BELOW_THRESHOLD
  }

  public record Check(Run run, Residual residual) {}

  private NavReportHold() {}

  public static boolean holdsTheReport(Check check) {
    return check.run() == COMPLETED && check.residual() == AT_THRESHOLD;
  }
}
