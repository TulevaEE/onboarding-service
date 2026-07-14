package ee.tuleva.onboarding.fund.fees;

import ee.tuleva.onboarding.config.ScheduledTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ScheduledTest(FundFeeSyncJob.class)
@ActiveProfiles("production")
class FundFeeSyncJobScheduledTest {

  @MockitoBean PensionikeskusDailyStatisticsClient dailyStatisticsClient;
  @MockitoBean PensionikeskusFeeComparisonClient feeComparisonClient;
  @MockitoBean FundFeeUpdater fundFeeUpdater;

  @Test
  void cronExpressionsResolve() {}
}
