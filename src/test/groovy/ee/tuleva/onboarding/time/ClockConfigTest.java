package ee.tuleva.onboarding.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ClockConfigTest {

  private static final Instant NOW = Instant.parse("2026-08-31T10:15:30Z");

  private final ClockConfig clockConfig = new ClockConfig();

  @AfterEach
  void resetClock() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void delegatesInstantAndZoneToTheClockHolder() {
    Clock fixed = Clock.fixed(NOW, ZoneOffset.UTC);
    ClockHolder.setClock(fixed);

    Clock delegatingClock = clockConfig.clock();

    assertThat(delegatingClock).isNotNull();
    assertThat(delegatingClock.instant()).isEqualTo(NOW);
    assertThat(delegatingClock.getZone()).isEqualTo(ZoneOffset.UTC);
  }

  @Test
  void withZoneDelegatesToTheCurrentClockHolderClock() {
    Clock fixed = Clock.fixed(NOW, ZoneOffset.UTC);
    ClockHolder.setClock(fixed);
    ZoneOffset otherZone = ZoneOffset.ofHours(2);

    Clock rezoned = clockConfig.clock().withZone(otherZone);

    assertThat(rezoned).isEqualTo(fixed.withZone(otherZone));
  }
}
