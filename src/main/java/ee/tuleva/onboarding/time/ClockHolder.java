package ee.tuleva.onboarding.time;

import java.time.Clock;
import java.time.ZoneId;

public class ClockHolder {

  private static ZoneId timeZone = ZoneId.systemDefault();
  private static Clock clock = Clock.system(timeZone);

  public static void setClock(Clock newClock) {
    clock = newClock;
  }
  public static Clock clock() {
    return clock;
  }

  public static Clock clock(ZoneId zone) {
    return Clock.system(zone);
  }
}
