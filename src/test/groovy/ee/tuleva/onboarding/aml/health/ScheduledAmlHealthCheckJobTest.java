package ee.tuleva.onboarding.aml.health;

import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.aml.AmlCheckType;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduledAmlHealthCheckJobTest {

  @Mock private AmlHealthCheckService mockAmlHealthCheckService;

  @InjectMocks private ScheduledAmlHealthCheckJob scheduledJob;

  private static final Set<AmlCheckType> SKIPPED_IN_JOB =
      Set.of(
          AmlCheckType.POLITICALLY_EXPOSED_PERSON_OVERRIDE,
          AmlCheckType.RISK_LEVEL_OVERRIDE,
          AmlCheckType.RISK_LEVEL_OVERRIDE_CONFIRMATION,
          AmlCheckType.SANCTION_OVERRIDE,
          AmlCheckType.INTERNAL_ESCALATION);

  @Test
  @DisplayName("checkForDelayedAmlChecks logs error when a type is delayed")
  void checkForDelayedAmlChecks_logsError_whenTypeIsDelayed() {
    // given
    AmlCheckType delayedType = AmlCheckType.DOCUMENT;
    AmlCheckType okType = AmlCheckType.CONTACT_DETAILS;

    when(mockAmlHealthCheckService.isCheckTypeDelayed(delayedType)).thenReturn(true);
    when(mockAmlHealthCheckService.isCheckTypeDelayed(okType)).thenReturn(false);

    // when
    scheduledJob.checkForDelayedAmlChecks();

    // then
    verify(mockAmlHealthCheckService).isCheckTypeDelayed(delayedType);
    verify(mockAmlHealthCheckService).isCheckTypeDelayed(okType);
  }

  @Test
  @DisplayName("checkForDelayedAmlChecks continues if one type check fails")
  void checkForDelayedAmlChecks_continues_whenOneTypeCheckFails() {
    // given
    AmlCheckType typeThatCausesError = AmlCheckType.SANCTION; // Not in default SKIPPED_IN_JOB
    AmlCheckType typeCheckedAfterError = AmlCheckType.RISK_LEVEL; // Not in default SKIPPED_IN_JOB

    when(mockAmlHealthCheckService.isCheckTypeDelayed(typeThatCausesError))
        .thenThrow(new RuntimeException("Simulated service error for SANCTION"));
    when(mockAmlHealthCheckService.isCheckTypeDelayed(typeCheckedAfterError)).thenReturn(false);

    // when
    scheduledJob.checkForDelayedAmlChecks();

    // then
    verify(mockAmlHealthCheckService).isCheckTypeDelayed(typeThatCausesError);
    verify(mockAmlHealthCheckService).isCheckTypeDelayed(typeCheckedAfterError);
  }

  @Test
  @DisplayName("checkForDelayedAmlChecks processes all relevant types and skips defined types")
  void checkForDelayedAmlChecks_processesAllRelevantTypesAndSkips() {
    // given
    for (AmlCheckType type : AmlCheckType.values()) {
      if (!SKIPPED_IN_JOB.contains(type)) {
        when(mockAmlHealthCheckService.isCheckTypeDelayed(type)).thenReturn(false);
      }
    }

    // when
    scheduledJob.checkForDelayedAmlChecks();

    // then
    for (AmlCheckType type : AmlCheckType.values()) {
      if (!SKIPPED_IN_JOB.contains(type)) {
        verify(mockAmlHealthCheckService).isCheckTypeDelayed(type);
      } else {
        verify(mockAmlHealthCheckService, never()).isCheckTypeDelayed(type);
      }
    }
  }
}
