package ee.tuleva.onboarding.investment.epis;

import ee.tuleva.onboarding.config.ScheduledTest;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ScheduledTest({PevaRavaPhaseUpdateJob.class, PevaRavaFlowRecalcJob.class, R16FlowRecalcJob.class})
@Import(PublicHolidays.class)
@ActiveProfiles("production")
class EpisReportJobsScheduledTest {

  @MockitoBean Clock clock;
  @MockitoBean PevaRavaPeriodService periodService;
  @MockitoBean PevaRavaCycleRepository cycleRepository;
  @MockitoBean PevaRavaFlowService flowService;
  @MockitoBean R16StatusService statusService;
  @MockitoBean OperationsNotificationService notificationService;

  @Test
  void cronExpressionsResolve() {}
}
