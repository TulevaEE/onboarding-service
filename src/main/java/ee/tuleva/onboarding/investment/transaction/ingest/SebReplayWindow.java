package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"production", "staging"})
class SebReplayWindow {

  private static final ZoneId SCHEDULE_ZONE = ZoneId.of(TIMEZONE);

  private final LocalTime closesAt;
  private final LocalTime opensAt;

  SebReplayWindow(
      @Value("${transaction-registry.seb-pending-replay.closes-at:07:45}") String closesAt,
      @Value("${transaction-registry.seb-pending-replay.opens-at:18:15}") String opensAt) {
    this.closesAt = LocalTime.parse(closesAt);
    this.opensAt = LocalTime.parse(opensAt);
    if (!this.closesAt.isBefore(this.opensAt)) {
      throw new IllegalStateException(
          "SEB pending replay window misconfigured, it must close before it opens again:"
              + " closesAt="
              + closesAt
              + ", opensAt="
              + opensAt);
    }
  }

  boolean isOpenAt(Instant instant) {
    LocalTime scheduleTime = instant.atZone(SCHEDULE_ZONE).toLocalTime();
    return scheduleTime.isBefore(closesAt) || !scheduleTime.isBefore(opensAt);
  }

  String closedHours() {
    return closesAt + "–" + opensAt;
  }

  String opensAt() {
    return opensAt.toString();
  }
}
