package ee.tuleva.onboarding.time;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static java.time.temporal.ChronoUnit.DAYS;

public class ClockHolder {

  private static ZoneId timeZone = ZoneId.systemDefault();
  private static Clock defaultClock = Clock.system(timeZone);
  private static Clock clock = defaultClock;

  public static void setClock(Clock newClock) {
    clock = newClock;
  }

  public static void setDefaultClock() {
    clock = defaultClock;
  }

  public static Clock clock() {
    return clock;
  }

  public static Instant aYearAgo() {
    return Instant.now(clock).minus(365, DAYS);
  }


  public static Clock clock(ZoneId zone) {
    return Clock.system(zone);
  }
}
