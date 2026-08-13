package ee.tuleva.onboarding.aml.risklevel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
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
  private static final double EXPECTED_PROBABILITY =
      MONTHLY_MEDIUM_RISK_TARGET_PROBABILITY / DAYS_IN_MONTH_ASSUMPTION_FOR_DAILY_RUN;

  @Test
  void staleLockFromAKilledInstanceExpiresWellBeforeTheNextScheduledRun()
      throws NoSuchMethodException {
    SchedulerLock lock =
        ScheduledRiskLevelCheckJob.class.getMethod("run").getAnnotation(SchedulerLock.class);

    assertThat(lock.lockAtMostFor()).isEqualTo("1h");
  }

  @Test
  void runCallsBothRiskLevelChecks() {
    scheduledRiskLevelCheckJob.run();

    verify(riskLevelService).runRiskLevelCheck(eq(EXPECTED_PROBABILITY));
    verify(riskLevelService).runTkfRiskLevelCheck(eq(EXPECTED_PROBABILITY));
  }

  @Test
  void tkfCheckStillRunsWhenThirdPillarCheckFails() {
    willThrow(new RuntimeException("III pillar failure"))
        .given(riskLevelService)
        .runRiskLevelCheck(anyDouble());

    scheduledRiskLevelCheckJob.run();

    verify(riskLevelService).runTkfRiskLevelCheck(eq(EXPECTED_PROBABILITY));
  }

  @Test
  void thirdPillarCheckStillRunsWhenTkfCheckFails() {
    willThrow(new RuntimeException("TKF failure"))
        .given(riskLevelService)
        .runTkfRiskLevelCheck(anyDouble());

    scheduledRiskLevelCheckJob.run();

    verify(riskLevelService).runRiskLevelCheck(eq(EXPECTED_PROBABILITY));
  }
}
