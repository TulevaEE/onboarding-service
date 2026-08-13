package ee.tuleva.onboarding.banking.payment;

import static ee.tuleva.onboarding.banking.payment.PaymentIntegrityCheck.FIELD_MISMATCH;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;
import static java.math.BigDecimal.TEN;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.time.MutableClock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentIntegrityNotifierTest {

  private static final String BENEFICIARY_IBAN = "EE222222222222222222";

  @Mock private OperationsNotificationService notificationService;

  private final MutableClock clock = new MutableClock();

  private PaymentIntegrityNotifier notifier() {
    return new PaymentIntegrityNotifier(notificationService, clock, Duration.ofHours(1));
  }

  @Test
  void blockedPaymentNotificationIdentifiesTheAccountWithoutLeakingTheIban() {
    notifier().onPaymentBlocked(blockedEvent());

    verify(notificationService)
        .sendMessage(
            argThat(message -> message.contains("…2222") && !message.contains(BENEFICIARY_IBAN)),
            eq(SAVINGS));
  }

  @Test
  void repeatedIdenticalFailuresAreNotifiedOnlyOnceWithinTheCooldown() {
    var notifier = notifier();

    notifier.onPaymentBlocked(blockedEvent());
    clock.tick(59, MINUTES);
    notifier.onPaymentBlocked(blockedEvent());

    verify(notificationService, times(1)).sendMessage(argThat(m -> true), eq(SAVINGS));
  }

  @Test
  void aFailureStillOutstandingAfterTheCooldownIsNotifiedAgain() {
    var notifier = notifier();

    notifier.onPaymentBlocked(blockedEvent());
    clock.tick(61, MINUTES);
    notifier.onPaymentBlocked(blockedEvent());

    verify(notificationService, times(2)).sendMessage(argThat(m -> true), eq(SAVINGS));
  }

  @Test
  void misroutedPaymentIsNotified() {
    notifier().onPaymentMisrouted(new PaymentMisroutedEvent(paymentRequest()));

    verify(notificationService)
        .sendMessage(argThat(message -> message.contains("NOT SENT")), eq(SAVINGS));
  }

  private PaymentBlockedEvent blockedEvent() {
    return new PaymentBlockedEvent(
        paymentRequest(),
        List.of(new PaymentIntegrityViolation(FIELD_MISMATCH, "beneficiaryIban")));
  }

  private PaymentRequest paymentRequest() {
    return PaymentRequest.builder()
        .remitterName("Tuleva Täiendav Kogumisfond")
        .remitterId("1162")
        .remitterIban("EE111111111111111111")
        .beneficiaryName("John Doe")
        .beneficiaryIban(BENEFICIARY_IBAN)
        .amount(TEN)
        .description("test payment")
        .ourId("123")
        .endToEndId("end-to-end-123")
        .build();
  }
}
