package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.*;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequestFixture.redemptionRequestFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
  @DisplayName("changeStatus transitions from RESERVED to VERIFIED")
  void changeStatus_reservedToVerified_succeeds() {
    var requestId = UUID.randomUUID();
    var request = redemptionRequestFixture().id(requestId).status(RESERVED).build();

    when(repository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));

    redemptionStatusService.changeStatus(requestId, VERIFIED);

    var captor = ArgumentCaptor.forClass(RedemptionRequest.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(VERIFIED);
  }

  @Test
  @DisplayName("changeStatus transitions from RESERVED to IN_REVIEW")
  void changeStatus_reservedToInReview_succeeds() {
    var requestId = UUID.randomUUID();
    var request = redemptionRequestFixture().id(requestId).status(RESERVED).build();

    when(repository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));

    redemptionStatusService.changeStatus(requestId, IN_REVIEW);

    var captor = ArgumentCaptor.forClass(RedemptionRequest.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(IN_REVIEW);
  }

  @Test
  @DisplayName("changeStatus transitions from RESERVED to CANCELLED")
  void changeStatus_reservedToCancelled_succeeds() {
    var requestId = UUID.randomUUID();
    var request = redemptionRequestFixture().id(requestId).status(RESERVED).build();

    when(repository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));

    redemptionStatusService.changeStatus(requestId, CANCELLED);

    var captor = ArgumentCaptor.forClass(RedemptionRequest.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(CANCELLED);
  }

  @Test
  @DisplayName("changeStatus transitions from VERIFIED to REDEEMED")
  void changeStatus_verifiedToRedeemed_succeeds() {
    var requestId = UUID.randomUUID();
    var request = redemptionRequestFixture().id(requestId).status(VERIFIED).build();

    when(repository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));

    redemptionStatusService.changeStatus(requestId, REDEEMED);

    var captor = ArgumentCaptor.forClass(RedemptionRequest.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(REDEEMED);
  }

  @Test
  @DisplayName("changeStatus transitions from REDEEMED to PROCESSED")
  void changeStatus_redeemedToProcessed_succeeds() {
    var requestId = UUID.randomUUID();
    var request = redemptionRequestFixture().id(requestId).status(REDEEMED).build();

    when(repository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));

    redemptionStatusService.changeStatus(requestId, PROCESSED);

    var captor = ArgumentCaptor.forClass(RedemptionRequest.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(PROCESSED);
  }

  @Test
  @DisplayName("changeStatus throws when transition not allowed")
  void changeStatus_invalidTransition_throwsException() {
    var requestId = UUID.randomUUID();
    var request = redemptionRequestFixture().id(requestId).status(RESERVED).build();

    when(repository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));

    assertThatThrownBy(() -> redemptionStatusService.changeStatus(requestId, PROCESSED))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("changeStatus throws when request not found")
  void changeStatus_notFound_throwsException() {
    var requestId = UUID.randomUUID();

    when(repository.findByIdForUpdate(requestId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> redemptionStatusService.changeStatus(requestId, VERIFIED))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("cancel cancels reserved request")
  void cancel_reservedRequest_succeeds() {
    var requestId = UUID.randomUUID();
    var request = redemptionRequestFixture().id(requestId).status(RESERVED).build();

    when(repository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));

    redemptionStatusService.cancel(requestId);

    var captor = ArgumentCaptor.forClass(RedemptionRequest.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(CANCELLED);
    assertThat(captor.getValue().getCancelledAt()).isNotNull();
  }

  @Test
  @DisplayName("cancel throws when request not in cancellable state")
  void cancel_notCancellable_throwsException() {
    var requestId = UUID.randomUUID();
    var request = redemptionRequestFixture().id(requestId).status(VERIFIED).build();

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
