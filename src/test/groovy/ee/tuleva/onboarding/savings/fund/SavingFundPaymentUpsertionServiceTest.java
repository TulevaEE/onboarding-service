package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.currency.Currency.EUR;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.SavingFundDeadlinesService;
import ee.tuleva.onboarding.savings.SavingFundPayment;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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
      new SavingFundPaymentUpsertionService(
          repository, deadlinesService, new NameMatcher(), Clock.systemUTC());

  @Test
  void cancelPayment_successful() {
    var party = new PartyId(PERSON, "38501010000");
    var paymentId = UUID.randomUUID();
    var payment =
        SavingFundPayment.builder().id(paymentId).partyId(party).amount(BigDecimal.TEN).build();

    when(repository.findById(paymentId)).thenReturn(Optional.of(payment));
    when(deadlinesService.getCancellationDeadline(payment))
        .thenReturn(Instant.now().plusSeconds(3600));

    service.cancelPayment(party, paymentId);

    verify(repository).cancel(paymentId);
  }

  @Test
  void cancelPayment_wrongUser_throwsException() {
    var paymentId = UUID.randomUUID();
    var payment =
        SavingFundPayment.builder()
            .id(paymentId)
            .partyId(new PartyId(PERSON, "38501010000"))
            .amount(BigDecimal.TEN)
            .build();

    when(repository.findById(paymentId)).thenReturn(Optional.of(payment));

    assertThrows(
        NoSuchElementException.class,
        () -> service.cancelPayment(new PartyId(PERSON, "49901010000"), paymentId));
    verify(repository, never()).cancel(any());
  }

  @Test
  void cancelPayment_deadlinePassed_throwsException() {
    var party = new PartyId(PERSON, "38501010000");
    var paymentId = UUID.randomUUID();
    var payment =
        SavingFundPayment.builder().id(paymentId).partyId(party).amount(BigDecimal.TEN).build();

    when(repository.findById(paymentId)).thenReturn(Optional.of(payment));
    when(deadlinesService.getCancellationDeadline(payment))
        .thenReturn(Instant.now().minusSeconds(3600));

    assertThrows(IllegalStateException.class, () -> service.cancelPayment(party, paymentId));
    verify(repository, never()).cancel(any());
  }

  @Test
  void cancelPayment_paymentNotFound_throwsException() {
    var party = new PartyId(PERSON, "38501010000");
    var paymentId = UUID.randomUUID();

    when(repository.findById(paymentId)).thenReturn(Optional.empty());

    assertThrows(NoSuchElementException.class, () -> service.cancelPayment(party, paymentId));
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
  void upsert_invokesOnInsertWithTheGeneratedPaymentId() {
    var generatedId = UUID.fromString("44444444-4444-4444-4444-444444444444");
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
    when(repository.findRecentPayments("37508295796")).thenReturn(List.of());
    when(repository.savePaymentData(incomingPayment)).thenReturn(generatedId);
    var seenByOnInsert = new java.util.concurrent.atomic.AtomicReference<SavingFundPayment>();

    service.upsert(
        incomingPayment,
        p -> {
          seenByOnInsert.set(p);
          return SavingFundPayment.Status.RECEIVED;
        });

    assertThat(seenByOnInsert.get().getId()).isEqualTo(generatedId);
    verify(repository).changeStatus(generatedId, SavingFundPayment.Status.RECEIVED);
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
  void upsert_overridesConflictingRemitterNameWithBankStatementName() {
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

    service.upsert(
        incomingPayment,
        p -> SavingFundPayment.Status.RECEIVED,
        p -> SavingFundPayment.Status.RECEIVED);

    var captor = ArgumentCaptor.forClass(SavingFundPayment.class);
    verify(repository).updatePaymentData(eq(existingPaymentId), captor.capture());

    var mergedPayment = captor.getValue();
    assertThat(mergedPayment.getRemitterName()).isEqualTo("MARI KASK");
  }

  @Test
  void upsert_overridesConflictingBeneficiaryNameWithBankStatementName() {
    var existingPaymentId = UUID.randomUUID();
    var existingPayment =
        SavingFundPayment.builder()
            .id(existingPaymentId)
            .amount(BigDecimal.TEN)
            .currency(EUR)
            .description("37508295796")
            .remitterName("MARI KASK")
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
            .remitterName("MARI KASK")
            .remitterIban("EE123")
            .beneficiaryName("TULEVA FONDID AS")
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
    assertThat(mergedPayment.getBeneficiaryName()).isEqualTo("TULEVA FONDID AS");
  }

  @Test
  void upsert_throwsExceptionWhenNonNameFieldDiffers() {
    var existingPaymentId = UUID.randomUUID();
    var existingPayment =
        SavingFundPayment.builder()
            .id(existingPaymentId)
            .amount(BigDecimal.TEN)
            .currency(EUR)
            .description("37508295796")
            .remitterName("MARI KASK")
            .remitterIban("EE123")
            .remitterIdCode("38888888888")
            .build();

    var incomingPayment =
        SavingFundPayment.builder()
            .amount(BigDecimal.TEN)
            .currency(EUR)
            .description("37508295796")
            .externalId("EXT123")
            .remitterName("MARI KASK")
            .remitterIban("EE123")
            .remitterIdCode("38888888889")
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

  private static final String CALLBACK_DESCRIPTION = "38888888888, 1788961806";
  private static final BigDecimal CALLBACK_AMOUNT = new BigDecimal("1500.00");
  private static final Instant CALLBACK_CREATED_AT = Instant.parse("2026-09-09T13:51:14Z");
  private static final Instant STATEMENT_RECEIVED_BEFORE = Instant.parse("2026-09-10T06:00:00Z");

  @Test
  void upsert_mergesIntoCreatedPaymentWithoutRemitterIban() {
    var callbackCreatedId = UUID.randomUUID();
    given(repository.findByExternalId("EXT-1")).willReturn(Optional.empty());
    given(repository.findRecentPayments(CALLBACK_DESCRIPTION))
        .willReturn(List.of(callbackCreatedPayment(callbackCreatedId)));

    service.upsert(
        statementPayment().build(),
        p -> SavingFundPayment.Status.RECEIVED,
        p -> SavingFundPayment.Status.RECEIVED);

    verify(repository).updatePaymentData(eq(callbackCreatedId), any());
    verify(repository).changeStatus(callbackCreatedId, SavingFundPayment.Status.RECEIVED);
    verify(repository, never()).savePaymentData(any());
  }

  @Test
  void upsert_prefersExactIbanMatchOverPaymentWithoutIban() {
    var withoutIbanId = UUID.randomUUID();
    var exactIbanId = UUID.randomUUID();
    var exactIbanPayment =
        callbackCreatedPayment(exactIbanId).toBuilder().remitterIban("EE123").build();
    given(repository.findByExternalId("EXT-1")).willReturn(Optional.empty());
    given(repository.findRecentPayments(CALLBACK_DESCRIPTION))
        .willReturn(List.of(callbackCreatedPayment(withoutIbanId), exactIbanPayment));

    service.upsert(
        statementPayment().build(),
        p -> SavingFundPayment.Status.RECEIVED,
        p -> SavingFundPayment.Status.RECEIVED);

    verify(repository).updatePaymentData(eq(exactIbanId), any());
    verify(repository, never()).updatePaymentData(eq(withoutIbanId), any());
    verify(repository, never()).savePaymentData(any());
  }

  @Test
  void upsert_mergesIntoOldestPaymentWithoutIbanWhenSeveralExist() {
    var olderId = UUID.randomUUID();
    var newerId = UUID.randomUUID();
    given(repository.findByExternalId("EXT-1")).willReturn(Optional.empty());
    given(repository.findRecentPayments(CALLBACK_DESCRIPTION))
        .willReturn(List.of(callbackCreatedPayment(olderId), callbackCreatedPayment(newerId)));

    service.upsert(
        statementPayment().build(),
        p -> SavingFundPayment.Status.RECEIVED,
        p -> SavingFundPayment.Status.RECEIVED);

    verify(repository).updatePaymentData(eq(olderId), any());
    verify(repository, never()).updatePaymentData(eq(newerId), any());
  }

  @Test
  void upsert_doesNotMergeIntoTerminalPaymentWithoutIban() {
    var processedId = UUID.randomUUID();
    var processedWithoutIban =
        callbackCreatedPayment(processedId).toBuilder()
            .status(SavingFundPayment.Status.PROCESSED)
            .build();
    given(repository.findByExternalId("EXT-1")).willReturn(Optional.empty());
    given(repository.findRecentPayments(CALLBACK_DESCRIPTION))
        .willReturn(List.of(processedWithoutIban));

    service.upsert(
        statementPayment().build(),
        p -> SavingFundPayment.Status.RECEIVED,
        p -> SavingFundPayment.Status.RECEIVED);

    verify(repository).savePaymentData(any());
    verify(repository, never()).updatePaymentData(any(), any());
  }

  @Test
  void upsert_keepsStatementEndToEndIdWhenEnriching() {
    var callbackCreatedId = UUID.randomUUID();
    given(repository.findByExternalId("EXT-1")).willReturn(Optional.empty());
    given(repository.findRecentPayments(CALLBACK_DESCRIPTION))
        .willReturn(List.of(callbackCreatedPayment(callbackCreatedId)));

    service.upsert(
        statementPayment().endToEndId("E2E-1").build(),
        p -> SavingFundPayment.Status.RECEIVED,
        p -> SavingFundPayment.Status.RECEIVED);

    verify(repository)
        .updatePaymentData(
            callbackCreatedId,
            statementPayment()
                .id(callbackCreatedId)
                .endToEndId("E2E-1")
                .createdAt(CALLBACK_CREATED_AT)
                .build());
  }

  @Test
  void upsert_takesTheStatementEndToEndIdInsteadOfFailingOnAnEarlierOne() {
    var existingId = UUID.randomUUID();
    var existing =
        callbackCreatedPayment(existingId).toBuilder()
            .remitterIban("EE123")
            .endToEndId("E2E-EARLIER")
            .build();
    given(repository.findByExternalId("EXT-1")).willReturn(Optional.empty());
    given(repository.findRecentPayments(CALLBACK_DESCRIPTION)).willReturn(List.of(existing));

    service.upsert(
        statementPayment().endToEndId("E2E-1").build(),
        p -> SavingFundPayment.Status.RECEIVED,
        p -> SavingFundPayment.Status.RECEIVED);

    verify(repository)
        .updatePaymentData(
            existingId,
            statementPayment()
                .id(existingId)
                .endToEndId("E2E-1")
                .createdAt(CALLBACK_CREATED_AT)
                .build());
  }

  @Test
  void upsert_doesNotMergeIntoPaymentWithoutIbanCreatedMoreThanAWeekBeforeTheBooking() {
    var staleId = UUID.randomUUID();
    var stale =
        callbackCreatedPayment(staleId).toBuilder()
            .createdAt(STATEMENT_RECEIVED_BEFORE.minus(Duration.ofDays(8)))
            .build();
    given(repository.findByExternalId("EXT-1")).willReturn(Optional.empty());
    given(repository.findRecentPayments(CALLBACK_DESCRIPTION)).willReturn(List.of(stale));

    service.upsert(
        statementPayment().build(),
        p -> SavingFundPayment.Status.RECEIVED,
        p -> SavingFundPayment.Status.RECEIVED);

    verify(repository).savePaymentData(any());
    verify(repository, never()).updatePaymentData(any(), any());
  }

  @Test
  void upsert_statementRowWithoutIbanDoesNotMergeIntoAStalePaymentWithoutIban() {
    var stale =
        callbackCreatedPayment(UUID.randomUUID()).toBuilder()
            .createdAt(STATEMENT_RECEIVED_BEFORE.minus(Duration.ofDays(8)))
            .build();
    given(repository.findByExternalId("EXT-1")).willReturn(Optional.empty());
    given(repository.findRecentPayments(CALLBACK_DESCRIPTION)).willReturn(List.of(stale));

    service.upsert(
        statementPayment().remitterIban(null).build(),
        p -> SavingFundPayment.Status.RECEIVED,
        p -> SavingFundPayment.Status.RECEIVED);

    verify(repository).savePaymentData(any());
    verify(repository, never()).updatePaymentData(any(), any());
  }

  @Test
  void upsert_measuresTheWeekFromNowWhenTheStatementRowHasNoBookingTime() {
    var clockedService =
        new SavingFundPaymentUpsertionService(
            repository,
            deadlinesService,
            new NameMatcher(),
            Clock.fixed(CALLBACK_CREATED_AT.plus(Duration.ofDays(8)), ZoneOffset.UTC));
    given(repository.findByExternalId("EXT-1")).willReturn(Optional.empty());
    given(repository.findRecentPayments(CALLBACK_DESCRIPTION))
        .willReturn(List.of(callbackCreatedPayment(UUID.randomUUID())));

    clockedService.upsert(
        statementPayment().receivedBefore(null).build(),
        p -> SavingFundPayment.Status.RECEIVED,
        p -> SavingFundPayment.Status.RECEIVED);

    verify(repository).savePaymentData(any());
    verify(repository, never()).updatePaymentData(any(), any());
  }

  private SavingFundPayment callbackCreatedPayment(UUID id) {
    return SavingFundPayment.builder()
        .id(id)
        .amount(CALLBACK_AMOUNT)
        .currency(EUR)
        .description(CALLBACK_DESCRIPTION)
        .createdAt(CALLBACK_CREATED_AT)
        .build();
  }

  private SavingFundPayment.SavingFundPaymentBuilder statementPayment() {
    return SavingFundPayment.builder()
        .amount(CALLBACK_AMOUNT)
        .currency(EUR)
        .description(CALLBACK_DESCRIPTION)
        .externalId("EXT-1")
        .remitterIban("EE123")
        .remitterIdCode("38888888888")
        .remitterName("MARI MAASIKAS")
        .beneficiaryIban("EE456")
        .beneficiaryIdCode("14118923")
        .beneficiaryName("TULEVA FONDID AS")
        .receivedBefore(STATEMENT_RECEIVED_BEFORE);
  }
}
