package ee.tuleva.onboarding.savings.fund;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SavingFundPaymentUpsertionServiceTest {

  @Mock private SavingFundPaymentRepository repository;

  @Mock private SavingFundPaymentDeadlinesService deadlinesService;

  @InjectMocks private SavingFundPaymentUpsertionService service;

  @Test
  void cancelUserPayment_successful() {
    var paymentId = UUID.randomUUID();
    var payment =
        SavingFundPayment.builder().id(paymentId).userId(1L).amount(BigDecimal.TEN).build();

    when(repository.findById(paymentId)).thenReturn(Optional.of(payment));
    when(deadlinesService.getCancellationDeadline(payment))
        .thenReturn(Instant.now().plusSeconds(3600));

    service.cancelUserPayment(1L, paymentId);

    verify(repository).cancel(paymentId);
  }

  @Test
  void cancelUserPayment_wrongUser_throwsException() {
    var paymentId = UUID.randomUUID();
    var payment =
        SavingFundPayment.builder().id(paymentId).userId(2L).amount(BigDecimal.TEN).build();

    when(repository.findById(paymentId)).thenReturn(Optional.of(payment));

    assertThrows(NoSuchElementException.class, () -> service.cancelUserPayment(1L, paymentId));
    verify(repository, never()).cancel(any());
  }

  @Test
  void cancelUserPayment_deadlinePassed_throwsException() {
    var paymentId = UUID.randomUUID();
    var payment =
        SavingFundPayment.builder().id(paymentId).userId(1L).amount(BigDecimal.TEN).build();

    when(repository.findById(paymentId)).thenReturn(Optional.of(payment));
    when(deadlinesService.getCancellationDeadline(payment))
        .thenReturn(Instant.now().minusSeconds(3600));

    assertThrows(IllegalStateException.class, () -> service.cancelUserPayment(1L, paymentId));
    verify(repository, never()).cancel(any());
  }

  @Test
  void cancelUserPayment_paymentNotFound_throwsException() {
    var paymentId = UUID.randomUUID();
    var payment =
        SavingFundPayment.builder().id(paymentId).userId(1L).amount(BigDecimal.TEN).build();

    when(repository.findById(paymentId)).thenReturn(Optional.empty());

    assertThrows(NoSuchElementException.class, () -> service.cancelUserPayment(1L, paymentId));
    verify(repository, never()).cancel(any());
  }
}
