package ee.tuleva.onboarding.time

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class TestClockHolder {
  public static Instant now = Instant.parse("2020-01-01T14:13:15Z")
  public static Clock clock = Clock.fixed(now, ZoneOffset.UTC)
}
