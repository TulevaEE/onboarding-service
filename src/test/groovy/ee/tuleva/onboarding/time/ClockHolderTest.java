package ee.tuleva.onboarding.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ClockHolderTest {

  private static final Instant NOW = Instant.parse("2026-08-31T10:15:30Z");

  @AfterEach
  void resetClock() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void clockAndGetClockReturnTheConfiguredClock() {
    Clock fixed = Clock.fixed(NOW, ZoneOffset.UTC);
    ClockHolder.setClock(fixed);

    assertThat(ClockHolder.clock()).isSameAs(fixed);
    assertThat(ClockHolder.getClock()).isSameAs(fixed);
  }

  @Test
  void aYearAgoIsExactlyThreeHundredSixtyFiveDaysBeforeTheConfiguredClock() {
    ClockHolder.setClock(Clock.fixed(NOW, ZoneOffset.UTC));

    assertThat(ClockHolder.aYearAgo()).isEqualTo(NOW.minus(365, ChronoUnit.DAYS));
  }

  @Test
  void sixMonthsAgoIsExactlyOneHundredEightyDaysBeforeTheConfiguredClock() {
    ClockHolder.setClock(Clock.fixed(NOW, ZoneOffset.UTC));

    assertThat(ClockHolder.sixMonthsAgo()).isEqualTo(NOW.minus(180, ChronoUnit.DAYS));
  }
}
