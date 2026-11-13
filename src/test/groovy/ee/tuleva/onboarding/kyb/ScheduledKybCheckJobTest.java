package ee.tuleva.onboarding.kyb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class ScheduledKybCheckJobTest {

  private final KybMonitoringService kybMonitoringService = mock(KybMonitoringService.class);
  private final ScheduledKybCheckJob job = new ScheduledKybCheckJob(kybMonitoringService);

  @Test
  void delegatesToMonitoringService() {
    job.run();

    verify(kybMonitoringService).screenAllCompanies();
  }

  @Test
  void weeklyScreeningOfRelatedPersonsDelegatesToMonitoringService() {
    job.screenRelatedPersonsWeekly();

    verify(kybMonitoringService).screenRelatedPersonsOfAllCompanies();
  }

  @Test
  void relatedPersonsAreScreenedWeeklyOnSundayEvening() throws NoSuchMethodException {
    var schedule =
        ScheduledKybCheckJob.class
            .getMethod("screenRelatedPersonsWeekly")
            .getAnnotation(Scheduled.class);

    assertThat(schedule.cron()).isEqualTo("${kyb.jobs.related-persons.cron:0 40 19 * * SUN}");
    assertThat(schedule.zone()).isEqualTo("${kyb.jobs.monitoring.zone:Europe/Tallinn}");
  }
}
