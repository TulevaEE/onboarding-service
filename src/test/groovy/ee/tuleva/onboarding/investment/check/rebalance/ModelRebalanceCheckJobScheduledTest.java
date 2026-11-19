package ee.tuleva.onboarding.investment.check.rebalance;

import ee.tuleva.onboarding.config.ScheduledTest;
import ee.tuleva.onboarding.investment.calendar.EstonianCalendar;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ScheduledTest(ModelRebalanceCheckJob.class)
@ActiveProfiles("production")
class ModelRebalanceCheckJobScheduledTest {

  @MockitoBean RebalanceWindows rebalanceWindows;
  @MockitoBean OperationsNotificationService notificationService;
  @MockitoBean EstonianCalendar estonianCalendar;
  @MockitoBean Clock clock;

  @Test
  void cronExpressionsResolve() {}
}
