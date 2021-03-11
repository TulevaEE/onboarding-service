package ee.tuleva.onboarding.deadline

import java.time.Clock
import java.time.Instant

import static java.time.ZoneOffset.UTC

class MandateDeadlinesFixture {
  private static Clock clock = Clock.fixed(Instant.parse("2021-03-11T10:00:00Z"), UTC)
  private static PublicHolidays publicHolidays = new PublicHolidays(clock)

  static MandateDeadlines sampleDeadlines() {
    return new MandateDeadlines(clock, publicHolidays)
  }
}
