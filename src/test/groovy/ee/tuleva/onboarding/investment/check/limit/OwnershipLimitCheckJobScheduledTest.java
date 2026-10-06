package ee.tuleva.onboarding.investment.check.limit;

import ee.tuleva.onboarding.config.ScheduledTest;
import ee.tuleva.onboarding.deadline.BusinessDays;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ScheduledTest(OwnershipLimitCheckJob.class)
@ActiveProfiles("production")
class OwnershipLimitCheckJobScheduledTest {

  @MockitoBean OwnershipLimitCheckService service;
  @MockitoBean OwnershipLimitCheckNotifier notifier;
  @MockitoBean BusinessDays businessDays;
  @MockitoBean Clock clock;

  @Test
  void cronExpressionsResolve() {}
}
