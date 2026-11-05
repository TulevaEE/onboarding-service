package ee.tuleva.onboarding.fund.fees;

import ee.tuleva.onboarding.config.ScheduledTest;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ScheduledTest(SavingsFundManagementFeeSyncJob.class)
@ActiveProfiles("production")
class SavingsFundManagementFeeSyncJobScheduledTest {

  @MockitoBean SavingsFundManagementFeeSync savingsFundManagementFeeSync;
  @MockitoBean Clock clock;

  @Test
  void cronExpressionResolves() {}
}
