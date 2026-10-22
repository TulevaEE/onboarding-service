package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.SavingFundDeadlinesService;
import ee.tuleva.onboarding.savings.SavingFundPayment;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class SavingFundPaymentCancellationServiceTest {

  SavingFundPaymentRepository repository = mock(SavingFundPaymentRepository.class);
  SavingFundDeadlinesService deadlinesService = mock(SavingFundDeadlinesService.class);

  static final Instant NOW = Instant.parse("2026-10-12T10:00:00Z");
  static final String OPS_REASON = "Unsupported payment provider";

  SavingFundPaymentCancellationService service =
      new SavingFundPaymentCancellationService(repository, deadlinesService, Clock.fixed(NOW, UTC));

  @Test
  void cancelPayment_successful() {
    var party = new PartyId(PERSON, "38888888888");
    var paymentId = UUID.randomUUID();
    var payment =
        SavingFundPayment.builder().id(paymentId).partyId(party).amount(BigDecimal.TEN).build();

    given(repository.findById(paymentId)).willReturn(Optional.of(payment));
    given(deadlinesService.getCancellationDeadline(payment)).willReturn(NOW.plusSeconds(1));

    service.cancelPayment(party, paymentId);

    verify(repository).cancel(paymentId, "Kasutaja soovil");
  }

  @Test
  void cancelPayment_wrongUser_throwsException() {
    var paymentId = UUID.randomUUID();
    var payment =
        SavingFundPayment.builder()
            .id(paymentId)
            .partyId(new PartyId(PERSON, "38888888888"))
            .amount(BigDecimal.TEN)
            .build();

    given(repository.findById(paymentId)).willReturn(Optional.of(payment));

    assertThatThrownBy(() -> service.cancelPayment(new PartyId(PERSON, "49999999999"), paymentId))
        .isInstanceOf(NoSuchElementException.class);
    verify(repository, never()).cancel(any(), any());
  }

  @Test
  void cancelPayment_alreadyCancelled_throwsException() {
    var party = new PartyId(PERSON, "38888888888");
    var paymentId = UUID.randomUUID();
    var payment =
        SavingFundPayment.builder()
            .id(paymentId)
            .partyId(party)
            .amount(BigDecimal.TEN)
            .cancelledAt(NOW.minusSeconds(60))
            .build();

    given(repository.findById(paymentId)).willReturn(Optional.of(payment));

    assertThatThrownBy(() -> service.cancelPayment(party, paymentId))
        .isInstanceOf(NoSuchElementException.class);
    verify(repository, never()).cancel(any(), any());
  }

  @Test
  void cancelPayment_deadlinePassed_throwsException() {
    var party = new PartyId(PERSON, "38888888888");
    var paymentId = UUID.randomUUID();
    var payment =
        SavingFundPayment.builder().id(paymentId).partyId(party).amount(BigDecimal.TEN).build();

    given(repository.findById(paymentId)).willReturn(Optional.of(payment));
    given(deadlinesService.getCancellationDeadline(payment)).willReturn(NOW.minusSeconds(1));

    assertThatThrownBy(() -> service.cancelPayment(party, paymentId))
        .isInstanceOf(PaymentCancellationRefusedException.class);
    verify(repository, never()).cancel(any(), any());
  }

  @Test
  void cancelPayment_paymentNotFound_throwsException() {
    var party = new PartyId(PERSON, "38888888888");
    var paymentId = UUID.randomUUID();

    given(repository.findById(paymentId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.cancelPayment(party, paymentId))
        .isInstanceOf(NoSuchElementException.class);
    verify(repository, never()).cancel(any(), any());
  }

  @Test
  void cancelPaymentById_cancelsPaymentOfAnyPartyAndReturnsItReread() {
    var paymentId = UUID.randomUUID();
    var party = new PartyId(PERSON, "38888888888");
    var payment =
        SavingFundPayment.builder().id(paymentId).partyId(party).amount(BigDecimal.TEN).build();
    var cancelledPayment =
        SavingFundPayment.builder()
            .id(paymentId)
            .partyId(party)
            .amount(BigDecimal.TEN)
            .cancelledAt(NOW)
            .build();

    given(repository.findById(paymentId))
        .willReturn(Optional.of(payment), Optional.of(cancelledPayment));
    given(deadlinesService.getCancellationDeadline(payment)).willReturn(NOW.plusSeconds(1));

    var result = service.cancelPayment(paymentId, OPS_REASON);

    assertThat(result).isEqualTo(cancelledPayment);
    verify(repository).cancel(paymentId, OPS_REASON);
    verify(repository, never()).addReturnReason(any(), any());
  }

  @Test
  void cancelPaymentById_alreadyCancelled_throwsException() {
    var paymentId = UUID.randomUUID();
    var payment =
        SavingFundPayment.builder()
            .id(paymentId)
            .partyId(new PartyId(PERSON, "38888888888"))
            .amount(BigDecimal.TEN)
            .cancelledAt(NOW.minusSeconds(60))
            .build();

    given(repository.findById(paymentId)).willReturn(Optional.of(payment));

    assertThatThrownBy(() -> service.cancelPayment(paymentId, OPS_REASON))
        .isInstanceOf(PaymentCancellationRefusedException.class);
    verify(repository, never()).cancel(any(), any());
  }

  @Test
  void cancelPaymentById_deadlinePassed_throwsException() {
    var paymentId = UUID.randomUUID();
    var payment =
        SavingFundPayment.builder()
            .id(paymentId)
            .partyId(new PartyId(PERSON, "38888888888"))
            .amount(BigDecimal.TEN)
            .build();

    given(repository.findById(paymentId)).willReturn(Optional.of(payment));
    given(deadlinesService.getCancellationDeadline(payment)).willReturn(NOW.minusSeconds(1));

    assertThatThrownBy(() -> service.cancelPayment(paymentId, OPS_REASON))
        .isInstanceOf(PaymentCancellationRefusedException.class);
    verify(repository, never()).cancel(any(), any());
  }

  @Test
  void cancelPaymentById_unattributedPayment_throwsException() {
    var paymentId = UUID.randomUUID();
    var payment = SavingFundPayment.builder().id(paymentId).amount(BigDecimal.TEN).build();

    given(repository.findById(paymentId)).willReturn(Optional.of(payment));
    given(deadlinesService.getCancellationDeadline(payment)).willReturn(NOW.plusSeconds(1));

    assertThatThrownBy(() -> service.cancelPayment(paymentId, OPS_REASON))
        .isInstanceOf(PaymentCancellationRefusedException.class);
    verify(repository, never()).cancel(any(), any());
  }

  @Test
  void cancelPaymentById_paymentNotFound_throwsException() {
    var paymentId = UUID.randomUUID();

    given(repository.findById(paymentId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.cancelPayment(paymentId, OPS_REASON))
        .isInstanceOf(NoSuchElementException.class);
    verify(repository, never()).cancel(any(), any());
  }

  static Stream<String> invalidCancellationReasons() {
    return Stream.of("", "   ", "x".repeat(131), "Vale summa\nteine rida", "Vale\tsumma");
  }

  @ParameterizedTest
  @MethodSource("invalidCancellationReasons")
  void cancelPaymentById_invalidReason_isRefusedBeforeTouchingRepository(String reason) {
    var paymentId = UUID.randomUUID();

    assertThatThrownBy(() -> service.cancelPayment(paymentId, reason))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(repository, deadlinesService);
  }

  @Test
  void cancelPaymentById_reasonFillingRefundDescription_isAccepted() {
    var paymentId = UUID.randomUUID();
    var reason = "x".repeat(130);
    givenCancellablePayment(paymentId);

    service.cancelPayment(paymentId, reason);

    verify(repository).cancel(paymentId, reason);
  }

  @Test
  void cancelPaymentById_reasonWithEstonianLetters_isAccepted() {
    var paymentId = UUID.randomUUID();
    var reason = "Õiguslik alus puudub: äöü šž";
    givenCancellablePayment(paymentId);

    service.cancelPayment(paymentId, reason);

    verify(repository).cancel(paymentId, reason);
  }

  @Test
  void cancelPaymentById_reasonWithSurroundingWhitespace_isStoredStripped() {
    var paymentId = UUID.randomUUID();
    givenCancellablePayment(paymentId);

    service.cancelPayment(paymentId, "  reason  ");

    verify(repository).cancel(paymentId, "reason");
  }

  private void givenCancellablePayment(UUID paymentId) {
    var payment =
        SavingFundPayment.builder()
            .id(paymentId)
            .partyId(new PartyId(PERSON, "38888888888"))
            .amount(BigDecimal.TEN)
            .build();
    given(repository.findById(paymentId)).willReturn(Optional.of(payment));
    given(deadlinesService.getCancellationDeadline(payment)).willReturn(NOW.plusSeconds(1));
  }
}
