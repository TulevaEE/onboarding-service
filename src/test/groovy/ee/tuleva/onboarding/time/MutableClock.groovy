package ee.tuleva.onboarding.time

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalUnit

import static java.time.ZoneOffset.UTC
import static java.time.temporal.ChronoUnit.SECONDS

class MutableClock extends Clock {

  private static final Instant DEFAULT_NOW = TestClockHolder.now
  private volatile Instant now
  private final ZoneId zone = UTC

  MutableClock() {
    this(DEFAULT_NOW)
  }

  MutableClock(Instant now) {
    this.now = now
  }

  void tick(long amountToAdd = 1, TemporalUnit unit = SECONDS) {
    now = now.plus(amountToAdd, unit)
  }

  @Override
  ZoneId getZone() { zone }

  @Override
  Clock withZone(ZoneId zone) { this }

  @Override
  Instant instant() { now }
}
