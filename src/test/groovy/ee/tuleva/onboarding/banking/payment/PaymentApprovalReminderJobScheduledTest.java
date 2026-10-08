package ee.tuleva.onboarding.banking.payment;

import ee.tuleva.onboarding.config.ScheduledTest;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ScheduledTest(PaymentApprovalReminderJob.class)
@Import(PublicHolidays.class)
@ActiveProfiles("production")
class PaymentApprovalReminderJobScheduledTest {

  @MockitoBean BriefedBatches briefedBatches;
  @MockitoBean PaymentApprovalReminder reminder;
  @MockitoBean PaymentSettlementCheck settlementCheck;
  @MockitoBean OperationsNotificationService notificationService;
  @MockitoBean Clock clock;

  @Test
  void cronExpressionsResolve() {}
}
