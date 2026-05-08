package ee.tuleva.onboarding.savings.fund.nav;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NavReconciliationJobTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  @Mock private NavReconciliationService reconciliationService;
  private final PublicHolidays publicHolidays = new PublicHolidays();

  @Test
  void reconcileDaily_reconcilesPreviousWorkingDay() {
    var job = jobOn("2026-05-07T14:00:00Z"); // Wednesday 16:00 Tallinn

    job.reconcileDaily();

    verify(reconciliationService).reconcile(LocalDate.of(2026, 5, 6));
  }

  @Test
  void reconcileDaily_skipsNonWorkingDay() {
    var job = jobOn("2026-05-09T14:00:00Z"); // Saturday

    job.reconcileDaily();

    verifyNoInteractions(reconciliationService);
  }

  @Test
  void reconcileDaily_skipsPublicHoliday() {
    var job = jobOn("2026-02-24T14:00:00Z"); // Estonian Independence Day (Tuesday)

    job.reconcileDaily();

    verifyNoInteractions(reconciliationService);
  }

  @Test
  void reconcileDaily_afterWeekend_reconcilesFriday() {
    var job = jobOn("2026-05-11T14:00:00Z"); // Monday 16:00 Tallinn

    job.reconcileDaily();

    verify(reconciliationService).reconcile(LocalDate.of(2026, 5, 8)); // Friday
  }

  private NavReconciliationJob jobOn(String instant) {
    Clock clock = Clock.fixed(Instant.parse(instant), TALLINN);
    return new NavReconciliationJob(reconciliationService, publicHolidays, clock);
  }
}
