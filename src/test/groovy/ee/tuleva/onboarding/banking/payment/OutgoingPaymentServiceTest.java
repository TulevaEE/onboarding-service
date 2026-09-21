package ee.tuleva.onboarding.banking.payment;

import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.ATTEMPTED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.FAILED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.SUBMITTED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentType.PAYOUT;
import static java.math.BigDecimal.TEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.time.MutableClock;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutgoingPaymentServiceTest {

  private static final String END_TO_END_ID = "end-to-end-123";

  @Mock private OutgoingPaymentRepository outgoingPaymentRepository;

  private final MutableClock clock = new MutableClock();

  private OutgoingPaymentService service() {
    return new OutgoingPaymentService(outgoingPaymentRepository, clock);
  }

  @Test
  void recordsWhatWasSentBeforeTheCall() {
    when(outgoingPaymentRepository.findByEndToEndId(END_TO_END_ID)).thenReturn(Optional.empty());

    service().recordAttempt(paymentRequest(), PAYOUT, UUID.randomUUID(), null, "<xml/>");

    var saved = ArgumentCaptor.forClass(OutgoingPayment.class);
    verify(outgoingPaymentRepository).save(saved.capture());
    assertThat(saved.getValue().getStatus()).isEqualTo(ATTEMPTED);
    assertThat(saved.getValue().getPaymentType()).isEqualTo(PAYOUT);
    assertThat(saved.getValue().getAmount()).isEqualTo(TEN);
    assertThat(saved.getValue().getBodyHash()).isNotBlank();
  }

  @Test
  void theBodyHashIdentifiesTheExactBytesSubmitted() {
    when(outgoingPaymentRepository.findByEndToEndId(END_TO_END_ID)).thenReturn(Optional.empty());

    service().recordAttempt(paymentRequest(), PAYOUT, null, null, "<xml>a</xml>");
    var first = captureSaved().getBodyHash();

    service().recordAttempt(paymentRequest(), PAYOUT, null, null, "<xml>b</xml>");
    assertThat(captureSaved().getBodyHash()).isNotEqualTo(first);
  }

  @Test
  void refusesToResendAPaymentAlreadySubmitted() {
    when(outgoingPaymentRepository.findByEndToEndId(END_TO_END_ID))
        .thenReturn(Optional.of(rowWith(SUBMITTED)));

    assertThatThrownBy(
            () -> service().recordAttempt(paymentRequest(), PAYOUT, null, null, "<xml/>"))
        .isInstanceOf(OutgoingPaymentBlockedException.class);

    verify(outgoingPaymentRepository, never()).save(any());
  }

  @Test
  void refusesToResendAPaymentStillInFlightBecauseItMayHaveExecuted() {
    when(outgoingPaymentRepository.findByEndToEndId(END_TO_END_ID))
        .thenReturn(Optional.of(rowWith(ATTEMPTED)));

    assertThatThrownBy(
            () -> service().recordAttempt(paymentRequest(), PAYOUT, null, null, "<xml/>"))
        .isInstanceOf(OutgoingPaymentBlockedException.class);
  }

  @Test
  void allowsRetryingAPaymentTheBankDefinitivelyRejected() {
    when(outgoingPaymentRepository.findByEndToEndId(END_TO_END_ID))
        .thenReturn(Optional.of(rowWith(FAILED)));

    service().recordAttempt(paymentRequest(), PAYOUT, null, null, "<xml/>");

    assertThat(captureSaved().getStatus()).isEqualTo(ATTEMPTED);
    assertThat(captureSaved().getFailureReason()).isNull();
  }

  @Test
  void resolvesToSubmitted() {
    when(outgoingPaymentRepository.findByEndToEndId(END_TO_END_ID))
        .thenReturn(Optional.of(rowWith(ATTEMPTED)));

    service().recordSubmitted(END_TO_END_ID);

    assertThat(captureSaved().getStatus()).isEqualTo(SUBMITTED);
    assertThat(captureSaved().getResolvedAt()).isNotNull();
  }

  @Test
  void resolvesToFailedWithTheReason() {
    when(outgoingPaymentRepository.findByEndToEndId(END_TO_END_ID))
        .thenReturn(Optional.of(rowWith(ATTEMPTED)));

    service().recordFailed(END_TO_END_ID, "rejected by the bank");

    assertThat(captureSaved().getStatus()).isEqualTo(FAILED);
    assertThat(captureSaved().getFailureReason()).isEqualTo("rejected by the bank");
  }

  private OutgoingPayment captureSaved() {
    var saved = ArgumentCaptor.forClass(OutgoingPayment.class);
    verify(outgoingPaymentRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
    return saved.getValue();
  }

  private static OutgoingPayment rowWith(OutgoingPaymentStatus status) {
    var row = new OutgoingPayment();
    row.setEndToEndId(END_TO_END_ID);
    row.setStatus(status);
    return row;
  }

  private static PaymentRequest paymentRequest() {
    return PaymentRequest.builder()
        .remitterName("Tuleva Täiendav Kogumisfond")
        .remitterId("1162")
        .remitterIban("EE111111111111111111")
        .beneficiaryName("John Doe")
        .beneficiaryIban("EE222222222222222222")
        .amount(TEN)
        .description("test payment")
        .ourId("123")
        .endToEndId(END_TO_END_ID)
        .build();
  }
}
