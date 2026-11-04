package ee.tuleva.onboarding.investment.transaction.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class SebReplayLeaseTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  @Test
  void aReplayStartedOnSundayEveningHoldsTheLockUntilFiveMinutesBeforeMondaysFirstImport() {
    Instant sundayEvening = tallinn("2026-05-17T22:00");

    SebReplayLease lease = SebReplayLease.startingAt(sundayEvening).orElseThrow();

    assertThat(lease.lockAtMostFor(sundayEvening)).isEqualTo(Duration.ofHours(9).plusMinutes(55));
  }

  @Test
  void aReplayIsRefusedWhileSebReportsAreImportedEveryFiveMinutes() {
    assertThat(SebReplayLease.startingAt(tallinn("2026-05-18T10:00"))).isEmpty();
  }

  @Test
  void aReplayIsRefusedForAQuarterHourAfterTheDaysLastImportStarted() {
    assertThat(SebReplayLease.startingAt(tallinn("2026-05-18T18:05"))).isEmpty();
    assertThat(SebReplayLease.startingAt(tallinn("2026-05-18T18:11"))).isPresent();
  }

  @Test
  void aReplayIsRefusedWhenTooLittleTimeIsLeftBeforeTheMorningsFirstImport() {
    assertThat(SebReplayLease.startingAt(tallinn("2026-05-18T07:50"))).isEmpty();
  }

  @Test
  void aRunningReplayIsOverTenMinutesBeforeItsLockIsFreed() {
    SebReplayLease lease = SebReplayLease.startingAt(tallinn("2026-05-17T22:00")).orElseThrow();

    assertThat(lease.isOver(tallinn("2026-05-18T07:44"))).isFalse();
    assertThat(lease.isOver(tallinn("2026-05-18T07:45"))).isTrue();
  }

  private static Instant tallinn(String localDateTime) {
    return LocalDateTime.parse(localDateTime).atZone(TALLINN).toInstant();
  }
}
