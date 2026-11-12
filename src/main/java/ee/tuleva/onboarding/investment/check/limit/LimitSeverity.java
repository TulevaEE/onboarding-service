package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.HARD;
import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.OK;
import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.SOFT;

import java.math.BigDecimal;

public final class LimitSeverity {

  public record Reading(BigDecimal actual, BigDecimal soft, BigDecimal hard) {}

  public record FreeCash(BigDecimal freeCash, BigDecimal maxFreeCash) {}

  private LimitSeverity() {}

  public static BreachSeverity positionSeverity(Reading reading) {
    if (reading.actual().compareTo(reading.hard()) > 0) {
      return HARD;
    }
    return reading.actual().compareTo(reading.soft()) > 0 ? SOFT : OK;
  }

  public static BreachSeverity reserveSeverity(Reading reading) {
    if (reading.actual().compareTo(reading.hard()) < 0) {
      return HARD;
    }
    return reading.actual().compareTo(reading.soft()) < 0 ? SOFT : OK;
  }

  public static BreachSeverity ownershipSeverity(Reading reading) {
    if (reading.actual().compareTo(reading.hard()) >= 0) {
      return HARD;
    }
    return reading.actual().compareTo(reading.soft()) > 0 ? SOFT : OK;
  }

  public static boolean freeCashAboveMaximum(FreeCash freeCash) {
    return freeCash.freeCash().compareTo(freeCash.maxFreeCash()) > 0;
  }
}
