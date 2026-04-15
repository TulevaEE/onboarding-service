package ee.tuleva.onboarding.savings.fund.nav;

import ee.tuleva.onboarding.config.ScheduledTest;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ScheduledTest(NavSelfHealJob.class)
@ActiveProfiles("production")
class NavSelfHealJobScheduledTest {

  @MockitoBean NavReportRepository navReportRepository;
  @MockitoBean NavCalculationJob navCalculationJob;
  @MockitoBean PublicHolidays publicHolidays;
  @MockitoBean Clock clock;
  @MockitoBean TaskScheduler taskScheduler;

  @Test
  void cronExpressionsResolve() {}
}
