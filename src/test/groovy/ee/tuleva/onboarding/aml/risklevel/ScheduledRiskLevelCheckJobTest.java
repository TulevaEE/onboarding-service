package ee.tuleva.onboarding.aml.risklevel;

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduledRiskLevelCheckJobTest {

  @Mock private RiskLevelService riskLevelService;

  @InjectMocks private ScheduledRiskLevelCheckJob scheduledRiskLevelCheckJob;

  private static final double MONTHLY_MEDIUM_RISK_TARGET_PROBABILITY = 0.025;
  private static final double DAYS_IN_MONTH_ASSUMPTION_FOR_DAILY_RUN = 30.0;
  private static final double EXPECTED_PROBABILITY_FOR_DAILY_RUN =
      MONTHLY_MEDIUM_RISK_TARGET_PROBABILITY / DAYS_IN_MONTH_ASSUMPTION_FOR_DAILY_RUN;

  @Test
  void runShouldInvokeRiskLevelServiceWithCorrectProbability() {
    scheduledRiskLevelCheckJob.run();
    verify(riskLevelService, times(1)).runRiskLevelCheck(eq(EXPECTED_PROBABILITY_FOR_DAILY_RUN));
  }
}
