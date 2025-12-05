package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.currency.Currency.EUR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SavingFundPaymentUpsertionServiceTest {

  SavingFundPaymentRepository repository = mock(SavingFundPaymentRepository.class);
  SavingFundDeadlinesService deadlinesService = mock(SavingFundDeadlinesService.class);

  SavingFundPaymentUpsertionService service =
      new SavingFundPaymentUpsertionService(repository, deadlinesService, new NameMatcher());

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

  @Test
  @DisplayName("upsert merges payment with flexible name matching")
  void upsert_mergesPaymentWithFlexibleNameMatching() {
    var existingPaymentId = UUID.randomUUID();
    var existingPayment =
        SavingFundPayment.builder()
            .id(existingPaymentId)
            .amount(BigDecimal.TEN)
            .currency(EUR)
            .description("37508295796")
            .remitterName("vootele Töömees")
            .remitterIban("EE123")
            .beneficiaryName("TULEVA AS")
            .beneficiaryIban("EE456")
            .build();

    var incomingPayment =
        SavingFundPayment.builder()
            .amount(BigDecimal.TEN)
            .currency(EUR)
            .description("37508295796")
            .externalId("EXT123")
            .remitterName("TOOMEES VOOTELE")
            .remitterIban("EE123")
            .beneficiaryName("TULEVA AS")
            .beneficiaryIban("EE456")
            .receivedBefore(Instant.now())
            .build();

    when(repository.findByExternalId("EXT123")).thenReturn(Optional.empty());
    when(repository.findRecentPayments("37508295796")).thenReturn(List.of(existingPayment));

    service.upsert(
        incomingPayment,
        p -> SavingFundPayment.Status.RECEIVED,
        p -> SavingFundPayment.Status.RECEIVED);

    var captor = ArgumentCaptor.forClass(SavingFundPayment.class);
    verify(repository).updatePaymentData(eq(existingPaymentId), captor.capture());

    var mergedPayment = captor.getValue();
    assertThat(mergedPayment.getRemitterName()).isEqualTo("vootele Töömees");
  }

  @Test
  @DisplayName("upsert throws exception when names don't match")
  void upsert_throwsExceptionWhenNamesDontMatch() {
    var existingPaymentId = UUID.randomUUID();
    var existingPayment =
        SavingFundPayment.builder()
            .id(existingPaymentId)
            .amount(BigDecimal.TEN)
            .currency(EUR)
            .description("37508295796")
            .remitterName("PEETER MEETER")
            .remitterIban("EE123")
            .build();

    var incomingPayment =
        SavingFundPayment.builder()
            .amount(BigDecimal.TEN)
            .currency(EUR)
            .description("37508295796")
            .externalId("EXT123")
            .remitterName("MARI KASK")
            .remitterIban("EE123")
            .receivedBefore(Instant.now())
            .build();

    when(repository.findByExternalId("EXT123")).thenReturn(Optional.empty());
    when(repository.findRecentPayments("37508295796")).thenReturn(List.of(existingPayment));

    assertThrows(
        IllegalStateException.class,
        () ->
            service.upsert(
                incomingPayment,
                p -> SavingFundPayment.Status.RECEIVED,
                p -> SavingFundPayment.Status.RECEIVED));
  }
}
