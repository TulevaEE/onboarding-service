package ee.tuleva.onboarding.investment.fundpage;

import ee.tuleva.onboarding.config.ScheduledTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ScheduledTest(FundPageDisclosureCheckJob.class)
@ActiveProfiles("production")
class FundPageDisclosureCheckJobScheduledTest {

  @MockitoBean FundPageDisclosureCheck check;
  @MockitoBean FundPageDisclosureNotifier notifier;

  @Test
  void cronExpressionsResolve() {}
}
