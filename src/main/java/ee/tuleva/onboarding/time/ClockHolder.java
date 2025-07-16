package ee.tuleva.onboarding.time;

import static java.time.temporal.ChronoUnit.DAYS;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

public class ClockHolder {

  private static final Clock defaultClock = Clock.systemUTC();
  private static Clock clock = defaultClock;

  public static void setClock(Clock newClock) {
    clock = newClock;
  }

  public static void setDefaultClock() {
    clock = defaultClock;
  }

  public static Clock getClock() {
    return clock();
  }

  public static Clock clock() {
    return clock;
  }

  public static Instant aYearAgo() {
    return Instant.now(clock).minus(365, DAYS);
  }

  public static Instant sixMonthsAgo() {
    return Instant.now(clock).minus(180, DAYS);
  }

  public static Clock clock(ZoneId zone) {
    return Clock.system(zone);
  }
}
