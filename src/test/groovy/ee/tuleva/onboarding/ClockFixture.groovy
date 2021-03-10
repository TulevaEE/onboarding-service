package ee.tuleva.onboarding

import java.time.Clock
import java.time.Instant
import java.time.ZoneId;

public class ClockFixture {
  public static Instant now = Instant.parse("2020-01-01T14:13:15Z")
  public static Clock clock = Clock.fixed(now, ZoneId.systemDefault())
}
