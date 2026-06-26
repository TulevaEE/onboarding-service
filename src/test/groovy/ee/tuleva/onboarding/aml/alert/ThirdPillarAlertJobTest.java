package ee.tuleva.onboarding.aml.alert;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ThirdPillarAlertJobTest {

  @Mock private ThirdPillarAlertService thirdPillarAlertService;
  @InjectMocks private ThirdPillarAlertJob job;

  @Test
  @DisplayName("delegates to the III pillar alert service")
  void run_callsService() {
    job.run();

    verify(thirdPillarAlertService).checkAndAlert();
  }

  @Test
  @DisplayName(
      "does not propagate a failure from the service so the scheduler lock releases cleanly")
  void run_serviceThrows_doesNotPropagate() {
    doThrow(new RuntimeException("boom")).when(thirdPillarAlertService).checkAndAlert();

    assertThatCode(() -> job.run()).doesNotThrowAnyException();

    verify(thirdPillarAlertService).checkAndAlert();
  }
}
