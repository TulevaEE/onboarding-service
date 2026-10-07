package ee.tuleva.onboarding.savings.fund;

import ee.tuleva.onboarding.config.ScheduledTest;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ScheduledTest(AccountOpenedEmailJob.class)
class AccountOpenedEmailJobScheduledTest {

  @MockitoBean OpenedAccountRepository repository;
  @MockitoBean ChildAccountOpenedEmailSender childSender;
  @MockitoBean AdultAccountOpenedEmailSender adultSender;
  @MockitoBean OperationsNotificationService notificationService;
  @MockitoBean Clock clock;

  @Test
  void cronExpressionsResolve() {}
}
