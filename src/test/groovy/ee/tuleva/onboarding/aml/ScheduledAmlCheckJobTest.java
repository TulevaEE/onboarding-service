package ee.tuleva.onboarding.aml;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduledAmlCheckJobTest {

  @Mock private AmlBatchScreener amlBatchScreener;

  @InjectMocks private ScheduledAmlCheckJob scheduledAmlCheckJob;

  @Test
  void run_shouldExecuteAmlChecksOnThirdPillarCustomers() {
    // when
    scheduledAmlCheckJob.run();

    // then
    verify(amlBatchScreener, times(1)).runAmlChecksOnThirdPillarCustomers();
  }

  @Test
  void run_shouldExecuteAmlChecksOnSavingsFundCustomers() {
    // when
    scheduledAmlCheckJob.run();

    // then
    verify(amlBatchScreener, times(1)).runAmlChecksOnSavingsFundCustomers();
  }
}
