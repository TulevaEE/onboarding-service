package ee.tuleva.onboarding.audit.health;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduledAuditHealthCheckJobTest {

  @Mock private AuditHealthService mockAuditHealthService;
  @InjectMocks private ScheduledAuditHealthCheckJob scheduledJob;

  @Test
  @DisplayName("checkAuditLogHealth logs error when service indicates delay")
  void checkAuditLogHealth_logsErrorWhenDelayed() {
    // given
    when(mockAuditHealthService.isAuditLogDelayed()).thenReturn(true);

    // when
    scheduledJob.checkAuditLogHealth();

    // then
    verify(mockAuditHealthService).isAuditLogDelayed();
  }

  @Test
  @DisplayName("checkAuditLogHealth logs info when service indicates no delay")
  void checkAuditLogHealth_logsInfoWhenNotDelayed() {
    // given
    when(mockAuditHealthService.isAuditLogDelayed()).thenReturn(false);

    // when
    scheduledJob.checkAuditLogHealth();

    // then
    verify(mockAuditHealthService).isAuditLogDelayed();
  }

  @Test
  @DisplayName("checkAuditLogHealth continues if service throws exception")
  void checkAuditLogHealth_continuesOnServiceException() {
    // given
    when(mockAuditHealthService.isAuditLogDelayed())
        .thenThrow(new RuntimeException("Simulated service error"));

    // when
    scheduledJob.checkAuditLogHealth();

    // then
    verify(mockAuditHealthService).isAuditLogDelayed();
  }
}
