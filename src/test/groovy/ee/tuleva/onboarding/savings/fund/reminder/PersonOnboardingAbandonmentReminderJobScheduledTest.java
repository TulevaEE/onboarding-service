package ee.tuleva.onboarding.savings.fund.reminder;

import ee.tuleva.onboarding.config.ScheduledTest;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ScheduledTest(PersonOnboardingAbandonmentReminderJob.class)
class PersonOnboardingAbandonmentReminderJobScheduledTest {

  @MockitoBean PersonOnboardingAbandonmentReminderRepository repository;
  @MockitoBean PersonOnboardingAbandonmentReminderSender sender;
  @MockitoBean OperationsNotificationService notificationService;
  @MockitoBean Clock clock;

  @Test
  void cronExpressionsResolve() {}
}
