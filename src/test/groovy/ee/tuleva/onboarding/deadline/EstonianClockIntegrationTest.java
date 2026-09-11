package ee.tuleva.onboarding.deadline;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class EstonianClockIntegrationTest {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");
  private static final ZoneId UTC = ZoneId.of("UTC");

  private final MandateDeadlinesService mandateDeadlinesService =
      new MandateDeadlinesService(Clock.system(ESTONIAN_ZONE), new PublicHolidays());

  @Test
  void deadlines_shouldUseEstonianTimezone_notUtc() {
    Instant decemberApplicationDate = Instant.parse("2024-12-01T10:00:00Z");
    MandateDeadlines deadlines = mandateDeadlinesService.getDeadlines(decemberApplicationDate);

    Instant thirdPillarDeadline = deadlines.getThirdPillarPaymentDeadline();

    ZonedDateTime deadlineUtc = thirdPillarDeadline.atZone(UTC);
    assertThat(deadlineUtc.getHour()).isEqualTo(13);

    ZonedDateTime deadlineEstonian = thirdPillarDeadline.atZone(ESTONIAN_ZONE);
    assertThat(deadlineEstonian.getHour()).isEqualTo(15);
  }
}
