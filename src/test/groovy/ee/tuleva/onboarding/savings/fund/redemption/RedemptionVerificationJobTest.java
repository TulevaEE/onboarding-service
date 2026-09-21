package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.RESERVED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequestFixture.redemptionRequestFixture;
import static org.mockito.Mockito.*;

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
    var request1 = redemptionRequestFixture().id(UUID.randomUUID()).userId(1L).build();
    var request2 = redemptionRequestFixture().id(UUID.randomUUID()).userId(2L).build();

    when(redemptionRequestRepository.findByStatus(RESERVED))
        .thenReturn(List.of(request1, request2));

    redemptionVerificationJob.runJob();

    verify(redemptionVerificationService).process(request1);
    verify(redemptionVerificationService).process(request2);
  }

  @Test
  @DisplayName("runJob continues processing when one request fails")
  void runJob_continuesProcessingWhenOneFails() {
    var request1 = redemptionRequestFixture().id(UUID.randomUUID()).userId(1L).build();
    var request2 = redemptionRequestFixture().id(UUID.randomUUID()).userId(2L).build();

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
}
