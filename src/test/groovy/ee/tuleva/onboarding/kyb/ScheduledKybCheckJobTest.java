package ee.tuleva.onboarding.kyb;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class ScheduledKybCheckJobTest {

  private final KybMonitoringService kybMonitoringService = mock(KybMonitoringService.class);
  private final ScheduledKybCheckJob job = new ScheduledKybCheckJob(kybMonitoringService);

  @Test
  void delegatesToMonitoringService() {
    job.run();

    verify(kybMonitoringService).screenAllCompanies();
  }
}
