package ee.tuleva.onboarding.aml.risklevel;

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

  @Test
  void runShouldInvokeRiskLevelService() {
    scheduledRiskLevelCheckJob.run();
    verify(riskLevelService, times(1)).runRiskLevelCheck();
  }
}
