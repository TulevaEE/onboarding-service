package ee.tuleva.onboarding.time;

import java.time.Clock;
import java.time.ZoneId;

public class ClockHolder {
  public static Clock CLOCK() {
    ZoneId timeZone = ZoneId.systemDefault();
    return Clock.system(timeZone);
  }
}
