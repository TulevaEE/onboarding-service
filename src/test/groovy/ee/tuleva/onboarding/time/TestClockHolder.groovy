package ee.tuleva.onboarding.time

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalUnit

import static java.time.ZoneOffset.UTC
import static java.time.temporal.ChronoUnit.SECONDS

class TestClockHolder {

  public static volatile Instant now = Instant.parse("2020-01-01T14:13:15Z")

  public static final Clock clock = new Clock() {
    @Override
    Instant instant() { now }

    @Override
    ZoneId getZone() { UTC }

    @Override
    Clock withZone(ZoneId zone) { this }
  }

  static void tick(long amountToAdd, TemporalUnit unit = SECONDS) {
    now = now.plus(amountToAdd, unit)
  }
}
