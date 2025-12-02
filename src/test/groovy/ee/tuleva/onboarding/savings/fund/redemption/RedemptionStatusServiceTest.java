package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RedemptionStatusServiceTest {

  @Mock private RedemptionRequestRepository repository;

  @InjectMocks private RedemptionStatusService redemptionStatusService;

  @Test
  @DisplayName("changeStatus transitions from PENDING to RESERVED")
  void changeStatus_pendingToReserved_succeeds() {
    var requestId = UUID.randomUUID();
    var request =
        RedemptionRequest.builder()
            .id(requestId)
            .userId(1L)
            .fundUnits(new BigDecimal("10.00000"))
            .customerIban("EE123456789012345678")
            .status(PENDING)
            .build();

    when(repository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));

    redemptionStatusService.changeStatus(requestId, RESERVED);

    var captor = ArgumentCaptor.forClass(RedemptionRequest.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(RESERVED);
  }

  @Test
  @DisplayName("changeStatus transitions from PENDING to CANCELLED")
  void changeStatus_pendingToCancelled_succeeds() {
    var requestId = UUID.randomUUID();
    var request =
        RedemptionRequest.builder()
            .id(requestId)
            .userId(1L)
            .fundUnits(new BigDecimal("10.00000"))
            .customerIban("EE123456789012345678")
            .status(PENDING)
            .build();

    when(repository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));

    redemptionStatusService.changeStatus(requestId, CANCELLED);

    var captor = ArgumentCaptor.forClass(RedemptionRequest.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(CANCELLED);
  }

  @Test
  @DisplayName("changeStatus transitions from RESERVED to PAID_OUT")
  void changeStatus_reservedToPaidOut_succeeds() {
    var requestId = UUID.randomUUID();
    var request =
        RedemptionRequest.builder()
            .id(requestId)
            .userId(1L)
            .fundUnits(new BigDecimal("10.00000"))
            .customerIban("EE123456789012345678")
            .status(RESERVED)
            .build();

    when(repository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));

    redemptionStatusService.changeStatus(requestId, PAID_OUT);

    var captor = ArgumentCaptor.forClass(RedemptionRequest.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(PAID_OUT);
  }

  @Test
  @DisplayName("changeStatus transitions from PAID_OUT to COMPLETED")
  void changeStatus_paidOutToCompleted_succeeds() {
    var requestId = UUID.randomUUID();
    var request =
        RedemptionRequest.builder()
            .id(requestId)
            .userId(1L)
            .fundUnits(new BigDecimal("10.00000"))
            .customerIban("EE123456789012345678")
            .status(PAID_OUT)
            .build();

    when(repository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));

    redemptionStatusService.changeStatus(requestId, COMPLETED);

    var captor = ArgumentCaptor.forClass(RedemptionRequest.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(COMPLETED);
  }

  @Test
  @DisplayName("changeStatus throws when transition not allowed")
  void changeStatus_invalidTransition_throwsException() {
    var requestId = UUID.randomUUID();
    var request =
        RedemptionRequest.builder()
            .id(requestId)
            .userId(1L)
            .fundUnits(new BigDecimal("10.00000"))
            .customerIban("EE123456789012345678")
            .status(PENDING)
            .build();

    when(repository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));

    assertThatThrownBy(() -> redemptionStatusService.changeStatus(requestId, COMPLETED))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("changeStatus throws when request not found")
  void changeStatus_notFound_throwsException() {
    var requestId = UUID.randomUUID();

    when(repository.findByIdForUpdate(requestId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> redemptionStatusService.changeStatus(requestId, RESERVED))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("cancel cancels pending request")
  void cancel_pendingRequest_succeeds() {
    var requestId = UUID.randomUUID();
    var request =
        RedemptionRequest.builder()
            .id(requestId)
            .userId(1L)
            .fundUnits(new BigDecimal("10.00000"))
            .customerIban("EE123456789012345678")
            .status(PENDING)
            .build();

    when(repository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));

    redemptionStatusService.cancel(requestId);

    var captor = ArgumentCaptor.forClass(RedemptionRequest.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(CANCELLED);
    assertThat(captor.getValue().getCancelledAt()).isNotNull();
  }

  @Test
  @DisplayName("cancel throws when request not pending")
  void cancel_notPending_throwsException() {
    var requestId = UUID.randomUUID();
    var request =
        RedemptionRequest.builder()
            .id(requestId)
            .userId(1L)
            .fundUnits(new BigDecimal("10.00000"))
            .customerIban("EE123456789012345678")
            .status(RESERVED)
            .build();

    when(repository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));

    assertThatThrownBy(() -> redemptionStatusService.cancel(requestId))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("cancel throws when request not found")
  void cancel_notFound_throwsException() {
    var requestId = UUID.randomUUID();

    when(repository.findByIdForUpdate(requestId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> redemptionStatusService.cancel(requestId))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
