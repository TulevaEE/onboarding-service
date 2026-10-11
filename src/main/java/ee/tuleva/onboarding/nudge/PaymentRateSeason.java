package ee.tuleva.onboarding.nudge;

import java.time.LocalDate;

public record PaymentRateSeason(LocalDate deadline, LocalDate fulfillmentDate, Mode mode) {

  public enum Mode {
    OFF_SEASON,
    SEASON,
    LAST_DAYS,
    CLOSED
  }

  boolean isShown() {
    return mode == Mode.SEASON || mode == Mode.LAST_DAYS;
  }

  boolean isClosed() {
    return mode == Mode.CLOSED;
  }
}
