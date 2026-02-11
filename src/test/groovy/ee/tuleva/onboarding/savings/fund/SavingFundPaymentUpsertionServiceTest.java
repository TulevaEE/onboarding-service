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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
  void upsert_doesNotMatchExistingPaymentWithDifferentAmount() {
    var existingPayment =
        SavingFundPayment.builder()
            .id(UUID.randomUUID())
            .amount(new BigDecimal("2079.00"))
            .currency(EUR)
            .description("37508295796")
            .remitterIban("EE123")
            .remitterName("JAAN TAMM")
            .build();

    var incomingPayment =
        SavingFundPayment.builder()
            .amount(new BigDecimal("400.00"))
            .currency(EUR)
            .description("37508295796")
            .externalId("EXT456")
            .remitterIban("EE123")
            .remitterName("JAAN TAMM")
            .receivedBefore(Instant.now())
            .build();

    when(repository.findByExternalId("EXT456")).thenReturn(Optional.empty());
    when(repository.findRecentPayments("37508295796")).thenReturn(List.of(existingPayment));
    when(repository.savePaymentData(incomingPayment)).thenReturn(UUID.randomUUID());

    service.upsert(
        incomingPayment,
        p -> SavingFundPayment.Status.RECEIVED,
        p -> SavingFundPayment.Status.RECEIVED);

    verify(repository).savePaymentData(incomingPayment);
    verify(repository, never()).updatePaymentData(any(), any());
  }

  @Test
  void upsert_matchesCorrectPaymentWhenMultipleExistWithSameDescription() {
    var matchingPaymentId = UUID.randomUUID();
    var matchingPayment =
        SavingFundPayment.builder()
            .id(matchingPaymentId)
            .amount(new BigDecimal("2079.00"))
            .currency(EUR)
            .description("30101129876")
            .remitterIban("EE123")
            .remitterName("KATI KARU")
            .build();

    var nonMatchingPayment =
        SavingFundPayment.builder()
            .id(UUID.randomUUID())
            .amount(new BigDecimal("400.00"))
            .currency(EUR)
            .description("30101129876")
            .remitterIban("EE123")
            .remitterName("KATI KARU")
            .build();

    var incomingPayment =
        SavingFundPayment.builder()
            .amount(new BigDecimal("2079.00"))
            .currency(EUR)
            .description("30101129876")
            .externalId("EXT789")
            .remitterIban("EE123")
            .remitterName("KATI KARU")
            .beneficiaryIban("EE456")
            .beneficiaryName("TULEVA AS")
            .receivedBefore(Instant.now())
            .build();

    when(repository.findByExternalId("EXT789")).thenReturn(Optional.empty());
    when(repository.findRecentPayments("30101129876"))
        .thenReturn(List.of(nonMatchingPayment, matchingPayment));

    service.upsert(
        incomingPayment,
        p -> SavingFundPayment.Status.RECEIVED,
        p -> SavingFundPayment.Status.RECEIVED);

    verify(repository).updatePaymentData(eq(matchingPaymentId), any());
    verify(repository, never()).savePaymentData(any());
  }

  @Test
  void upsert_matchesOldestUnclaimedPaymentWhenDuplicatesExist() {
    var firstPaymentId = UUID.randomUUID();
    var firstPayment =
        SavingFundPayment.builder()
            .id(firstPaymentId)
            .amount(new BigDecimal("400.00"))
            .currency(EUR)
            .description("30101129876")
            .remitterIban("EE123")
            .remitterName("KATI KARU")
            .build();

    var secondPaymentId = UUID.randomUUID();
    var secondPayment =
        SavingFundPayment.builder()
            .id(secondPaymentId)
            .amount(new BigDecimal("400.00"))
            .currency(EUR)
            .description("30101129876")
            .remitterIban("EE123")
            .remitterName("KATI KARU")
            .build();

    var firstIncoming =
        SavingFundPayment.builder()
            .amount(new BigDecimal("400.00"))
            .currency(EUR)
            .description("30101129876")
            .externalId("EXT_FIRST")
            .remitterIban("EE123")
            .remitterName("KATI KARU")
            .beneficiaryIban("EE456")
            .beneficiaryName("TULEVA AS")
            .receivedBefore(Instant.now())
            .build();

    var secondIncoming =
        SavingFundPayment.builder()
            .amount(new BigDecimal("400.00"))
            .currency(EUR)
            .description("30101129876")
            .externalId("EXT_SECOND")
            .remitterIban("EE123")
            .remitterName("KATI KARU")
            .beneficiaryIban("EE456")
            .beneficiaryName("TULEVA AS")
            .receivedBefore(Instant.now())
            .build();

    when(repository.findByExternalId("EXT_FIRST")).thenReturn(Optional.empty());
    when(repository.findByExternalId("EXT_SECOND")).thenReturn(Optional.empty());
    when(repository.findRecentPayments("30101129876"))
        .thenReturn(List.of(firstPayment, secondPayment));

    service.upsert(
        firstIncoming,
        p -> SavingFundPayment.Status.RECEIVED,
        p -> SavingFundPayment.Status.RECEIVED);

    verify(repository).updatePaymentData(eq(firstPaymentId), any());

    // After first upsert, firstPayment now has externalId set
    firstPayment =
        SavingFundPayment.builder()
            .id(firstPaymentId)
            .amount(new BigDecimal("400.00"))
            .currency(EUR)
            .description("30101129876")
            .externalId("EXT_FIRST")
            .remitterIban("EE123")
            .remitterName("KATI KARU")
            .build();

    when(repository.findRecentPayments("30101129876"))
        .thenReturn(List.of(firstPayment, secondPayment));

    service.upsert(
        secondIncoming,
        p -> SavingFundPayment.Status.RECEIVED,
        p -> SavingFundPayment.Status.RECEIVED);

    verify(repository).updatePaymentData(eq(secondPaymentId), any());
  }

  @Test
  void upsert_doesNotMatchCancelledPayment() {
    var cancelledPayment =
        SavingFundPayment.builder()
            .id(UUID.randomUUID())
            .amount(new BigDecimal("1.01"))
            .currency(EUR)
            .description("39107050268")
            .remitterIban("EE337700771002259573")
            .remitterName("MATI METS")
            .status(SavingFundPayment.Status.TO_BE_RETURNED)
            .build();

    var incomingPayment =
        SavingFundPayment.builder()
            .amount(new BigDecimal("1.01"))
            .currency(EUR)
            .description("39107050268")
            .externalId("EXT999")
            .remitterIban("EE337700771002259573")
            .remitterName("MATI METS")
            .receivedBefore(Instant.now())
            .build();

    when(repository.findByExternalId("EXT999")).thenReturn(Optional.empty());
    when(repository.findRecentPayments("39107050268")).thenReturn(List.of(cancelledPayment));
    when(repository.savePaymentData(incomingPayment)).thenReturn(UUID.randomUUID());

    service.upsert(
        incomingPayment,
        p -> SavingFundPayment.Status.RECEIVED,
        p -> SavingFundPayment.Status.RECEIVED);

    verify(repository).savePaymentData(incomingPayment);
    verify(repository, never()).updatePaymentData(any(), any());
  }

  @ParameterizedTest
  @EnumSource(
      value = SavingFundPayment.Status.class,
      names = {"RETURNED", "PROCESSED", "FROZEN"})
  void upsert_enrichesTerminalPaymentWithoutCreatingDuplicate(SavingFundPayment.Status status) {
    var existingPaymentId = UUID.randomUUID();
    var existingPayment =
        SavingFundPayment.builder()
            .id(existingPaymentId)
            .amount(new BigDecimal("1.01"))
            .currency(EUR)
            .description("39107050268")
            .remitterIban("EE337700771002259573")
            .remitterName("MATI METS")
            .status(status)
            .build();

    var externalId = "EXT_" + status;
    var incomingPayment =
        SavingFundPayment.builder()
            .amount(new BigDecimal("1.01"))
            .currency(EUR)
            .description("39107050268")
            .externalId(externalId)
            .remitterIban("EE337700771002259573")
            .remitterName("MATI METS")
            .beneficiaryIban("EE456")
            .beneficiaryName("TULEVA AS")
            .receivedBefore(Instant.now())
            .build();

    when(repository.findByExternalId(externalId)).thenReturn(Optional.empty());
    when(repository.findRecentPayments("39107050268")).thenReturn(List.of(existingPayment));

    service.upsert(
        incomingPayment,
        p -> SavingFundPayment.Status.RECEIVED,
        p -> SavingFundPayment.Status.RECEIVED);

    verify(repository).updatePaymentData(eq(existingPaymentId), any());
    verify(repository, never()).savePaymentData(any());
    verify(repository, never()).changeStatus(any(), any());
  }

  @Test
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
