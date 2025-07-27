package ee.tuleva.onboarding.time

import java.time.Clock
import java.time.Instant

import static java.time.ZoneOffset.UTC

class TestClockHolder {
  public static Instant now = Instant.parse("2020-01-01T14:13:15Z")
  public static Clock clock = Clock.fixed(now, UTC)
}
