package ee.tuleva.onboarding.investment.transaction.ingest;

import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class SebTradeListingCheckJobTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  private final SebTradeListingCheck check = mock(SebTradeListingCheck.class);

  @Test
  void runsTheCheckOnAWorkingDay() {
    jobOn("2026-02-16T08:15:00Z").run();

    then(check).should().run();
  }

  @Test
  void skipsTheCheckOnAnEstonianPublicHoliday() {
    jobOn("2026-02-24T08:15:00Z").run();

    then(check).shouldHaveNoInteractions();
  }

  private SebTradeListingCheckJob jobOn(String instant) {
    return new SebTradeListingCheckJob(
        Clock.fixed(Instant.parse(instant), TALLINN), new PublicHolidays(), check);
  }
}
