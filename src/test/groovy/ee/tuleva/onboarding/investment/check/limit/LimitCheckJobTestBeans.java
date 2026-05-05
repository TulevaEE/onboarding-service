package ee.tuleva.onboarding.investment.check.limit;

import static org.mockito.Mockito.mock;

import ee.tuleva.onboarding.investment.event.PipelineTracker;
import ee.tuleva.onboarding.investment.position.FeeAccrualPositionSyncJob;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class LimitCheckJobTestBeans {

  @Bean
  public FeeAccrualPositionSyncJob feeAccrualPositionSyncJob() {
    return mock(FeeAccrualPositionSyncJob.class);
  }

  @Bean
  public LimitCheckJob limitCheckJob(
      LimitCheckService limitCheckService,
      LimitCheckNotifier limitCheckNotifier,
      FeeAccrualPositionSyncJob feeAccrualPositionSyncJob,
      PipelineTracker pipelineTracker) {
    return new LimitCheckJob(
        limitCheckService, limitCheckNotifier, feeAccrualPositionSyncJob, pipelineTracker);
  }
}
