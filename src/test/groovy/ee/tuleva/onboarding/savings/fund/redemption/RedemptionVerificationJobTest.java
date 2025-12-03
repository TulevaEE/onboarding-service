package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.RESERVED;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RedemptionVerificationJobTest {

  @Mock private RedemptionRequestRepository redemptionRequestRepository;
  @Mock private RedemptionVerificationService redemptionVerificationService;

  @InjectMocks private RedemptionVerificationJob redemptionVerificationJob;

  @Test
  @DisplayName("runJob processes all reserved redemption requests")
  void runJob_processesAllReservedRequests() {
    var request1 = createRequest(UUID.randomUUID(), 1L);
    var request2 = createRequest(UUID.randomUUID(), 2L);

    when(redemptionRequestRepository.findByStatus(RESERVED))
        .thenReturn(List.of(request1, request2));

    redemptionVerificationJob.runJob();

    verify(redemptionVerificationService).process(request1);
    verify(redemptionVerificationService).process(request2);
  }

  @Test
  @DisplayName("runJob continues processing when one request fails")
  void runJob_continuesProcessingWhenOneFails() {
    var request1 = createRequest(UUID.randomUUID(), 1L);
    var request2 = createRequest(UUID.randomUUID(), 2L);

    when(redemptionRequestRepository.findByStatus(RESERVED))
        .thenReturn(List.of(request1, request2));
    doThrow(new RuntimeException("Processing failed"))
        .when(redemptionVerificationService)
        .process(request1);

    redemptionVerificationJob.runJob();

    verify(redemptionVerificationService).process(request1);
    verify(redemptionVerificationService).process(request2);
  }

  @Test
  @DisplayName("runJob does nothing when no reserved requests exist")
  void runJob_doesNothingWhenNoReservedRequests() {
    when(redemptionRequestRepository.findByStatus(RESERVED)).thenReturn(List.of());

    redemptionVerificationJob.runJob();

    verifyNoInteractions(redemptionVerificationService);
  }

  private RedemptionRequest createRequest(UUID id, Long userId) {
    return RedemptionRequest.builder()
        .id(id)
        .userId(userId)
        .fundUnits(new BigDecimal("10.00000"))
        .customerIban("EE123456789012345678")
        .status(RESERVED)
        .build();
  }
}
