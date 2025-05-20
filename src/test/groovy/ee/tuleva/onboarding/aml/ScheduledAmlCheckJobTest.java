package ee.tuleva.onboarding.aml;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduledAmlCheckJobTest {

  @Mock private AmlService amlService;

  @InjectMocks private ScheduledAmlCheckJob scheduledAmlCheckJob;

  @Test
  @DisplayName("Should call AmlService to run checks on third pillar customers when job runs")
  void run_shouldExecuteAmlChecksOnThirdPillarCustomers() {
    // when
    scheduledAmlCheckJob.run();

    // then
    verify(amlService, times(1)).runAmlChecksOnThirdPillarCustomers();
  }
}
