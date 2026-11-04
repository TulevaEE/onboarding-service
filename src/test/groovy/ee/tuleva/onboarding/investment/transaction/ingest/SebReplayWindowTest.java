package ee.tuleva.onboarding.investment.transaction.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class SebReplayWindowTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  private final SebReplayWindow window = new SebReplayWindow("07:45", "18:15");

  @Test
  void aReplayMayRunInTheEveningAndAtNightInTallinnTime() {
    assertThat(window.isOpenAt(tallinn("2026-05-18T18:15"))).isTrue();
    assertThat(window.isOpenAt(tallinn("2026-05-18T23:30"))).isTrue();
    assertThat(window.isOpenAt(tallinn("2026-05-19T07:44"))).isTrue();
  }

  @Test
  void aReplayMayNotRunWhileSebReportsAreImportedOrTheDailyRunIsDue() {
    assertThat(window.isOpenAt(tallinn("2026-05-18T07:45"))).isFalse();
    assertThat(window.isOpenAt(tallinn("2026-05-18T09:00"))).isFalse();
    assertThat(window.isOpenAt(tallinn("2026-05-18T18:14"))).isFalse();
  }

  @Test
  void aReplayStopsFortyFiveMinutesInBeforeTheJobTriggerPollersLockCanLapse() {
    Instant startedAt = tallinn("2026-05-18T22:00");

    assertThat(SebReplayWindow.stopByForAReplayStartedAt(startedAt))
        .isEqualTo(tallinn("2026-05-18T22:45"));
  }

  @Test
  void aClosedWindowThatDoesNotEndAfterItStartsIsRefusedAtStartup() {
    assertThatThrownBy(() -> new SebReplayWindow("18:15", "07:45"))
        .isInstanceOf(IllegalStateException.class);
  }

  private static Instant tallinn(String localDateTime) {
    return LocalDateTime.parse(localDateTime).atZone(TALLINN).toInstant();
  }
}
