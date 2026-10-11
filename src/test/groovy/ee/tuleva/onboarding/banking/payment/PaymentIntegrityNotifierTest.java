package ee.tuleva.onboarding.banking.payment;

import static ee.tuleva.onboarding.banking.payment.PaymentIntegrityCheck.FIELD_MISMATCH;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.TEN;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.time.MutableClock;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentIntegrityNotifierTest {

  private static final String BENEFICIARY_IBAN = "EE222222222222222222";
  private static final String REMITTER_IBAN = "EE111111111111111111";

  @Mock private OperationsNotificationService notificationService;

  private final MutableClock clock = new MutableClock();

  private PaymentIntegrityNotifier notifier() {
    return new PaymentIntegrityNotifier(notificationService, clock, Duration.ofHours(1));
  }

  @Test
  void blockedPaymentNotificationCarriesNoIbanAtAll() {
    notifier().onPaymentBlocked(blockedEvent());

    verify(notificationService)
        .sendMessage(
            argThat(
                message ->
                    message.contains("end-to-end-123")
                        && message.contains("beneficiaryIban")
                        && !message.contains(BENEFICIARY_IBAN)
                        && !message.contains("2222")),
            eq(INVESTMENT));
  }

  @Test
  void misroutedPaymentNotificationCarriesNoIbanAtAll() {
    notifier().onPaymentMisrouted(new PaymentMisroutedEvent(paymentRequest()));

    verify(notificationService)
        .sendMessage(
            argThat(
                message ->
                    !message.contains(REMITTER_IBAN)
                        && !message.contains("1111")
                        && !message.contains(BENEFICIARY_IBAN)),
            eq(INVESTMENT));
  }

  @Test
  void repeatedIdenticalFailuresAreNotifiedOnlyOnceWithinTheCooldown() {
    var notifier = notifier();

    notifier.onPaymentBlocked(blockedEvent());
    clock.tick(59, MINUTES);
    notifier.onPaymentBlocked(blockedEvent());

    verify(notificationService, times(1)).sendMessage(argThat(m -> true), eq(INVESTMENT));
  }

  @Test
  void aFailureStillOutstandingAfterTheCooldownIsNotifiedAgain() {
    var notifier = notifier();

    notifier.onPaymentBlocked(blockedEvent());
    clock.tick(61, MINUTES);
    notifier.onPaymentBlocked(blockedEvent());

    verify(notificationService, times(2)).sendMessage(argThat(m -> true), eq(INVESTMENT));
  }

  @Test
  void aDifferentPaymentFailingTheSameCheckIsNotifiedWithinTheCooldown() {
    var notifier = notifier();

    notifier.onPaymentBlocked(blockedEvent());
    clock.tick(1, MINUTES);
    notifier.onPaymentBlocked(blockedEvent("EE333333333333333333", ONE));

    verify(notificationService, times(2)).sendMessage(argThat(m -> true), eq(INVESTMENT));
  }

  @Test
  void misroutedPaymentIsNotified() {
    notifier().onPaymentMisrouted(new PaymentMisroutedEvent(paymentRequest()));

    verify(notificationService)
        .sendMessage(argThat(message -> message.contains("NOT SENT")), eq(INVESTMENT));
  }

  private PaymentBlockedEvent blockedEvent() {
    return blockedEvent(BENEFICIARY_IBAN, TEN);
  }

  private PaymentBlockedEvent blockedEvent(String beneficiaryIban, BigDecimal amount) {
    return new PaymentBlockedEvent(
        paymentRequest(beneficiaryIban, amount),
        List.of(new PaymentIntegrityViolation(FIELD_MISMATCH, "beneficiaryIban")));
  }

  private PaymentRequest paymentRequest() {
    return paymentRequest(BENEFICIARY_IBAN, TEN);
  }

  private PaymentRequest paymentRequest(String beneficiaryIban, BigDecimal amount) {
    return PaymentRequest.builder()
        .remitterName("Tuleva Täiendav Kogumisfond")
        .remitterId("1162")
        .remitterIban(REMITTER_IBAN)
        .beneficiaryName("John Doe")
        .beneficiaryIban(beneficiaryIban)
        .amount(amount)
        .description("test payment")
        .ourId("123")
        .endToEndId("end-to-end-123")
        .build();
  }
}
