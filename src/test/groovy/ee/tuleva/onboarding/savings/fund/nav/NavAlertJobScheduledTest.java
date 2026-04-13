package ee.tuleva.onboarding.savings.fund.nav;

import ee.tuleva.onboarding.config.ScheduledTest;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ScheduledTest(NavAlertJob.class)
@ActiveProfiles("production")
class NavAlertJobScheduledTest {

  @MockitoBean NavReportRepository navReportRepository;
  @MockitoBean OperationsNotificationService notificationService;
  @MockitoBean PublicHolidays publicHolidays;
  @MockitoBean Clock clock;

  @Test
  void cronExpressionsResolve() {}
}
