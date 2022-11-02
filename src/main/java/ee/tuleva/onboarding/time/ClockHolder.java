package ee.tuleva.onboarding.time;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static java.time.temporal.ChronoUnit.DAYS;

public class ClockHolder {

  private static ZoneId timeZone = ZoneId.systemDefault();
  private static Clock clock = Clock.system(timeZone);

  public static void setClock(Clock newClock) {
    clock = newClock;
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
