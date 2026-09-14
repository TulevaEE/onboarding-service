package ee.tuleva.onboarding.investment.check.limit;

import ee.tuleva.onboarding.config.ScheduledTest;
import ee.tuleva.onboarding.investment.position.FeeAccrualPositionSyncJob;
import ee.tuleva.onboarding.pipeline.PipelineTracker;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ScheduledTest(LimitCheckJob.class)
class LimitCheckJobScheduledTest {

  @MockitoBean LimitCheckService limitCheckService;
  @MockitoBean LimitCheckNotifier limitCheckNotifier;
  @MockitoBean FeeAccrualPositionSyncJob feeAccrualPositionSyncJob;
  @MockitoBean PipelineTracker pipelineTracker;

  @Test
  void cronExpressionsResolve() {}
}
