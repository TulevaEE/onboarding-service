package ee.tuleva.onboarding.banking.check.payment;

import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckSeverity.HOLD;
import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckType.PAYOUT_WITHOUT_REQUEST;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.time.MutableClock;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentCheckServiceTest {

  private static final String ENTRY_KEY = "entry-123";

  @Mock private PaymentCheckEventRepository paymentCheckEventRepository;
  @Mock private OperationsNotificationService notificationService;

  private final MutableClock clock = new MutableClock();

  private PaymentCheckService service() {
    return new PaymentCheckService(paymentCheckEventRepository, notificationService, clock);
  }

  @Test
  void anewFindingIsRecordedAndAlerted() {
    when(paymentCheckEventRepository.findByCheckTypeAndExternalKey(
            PAYOUT_WITHOUT_REQUEST, ENTRY_KEY))
        .thenReturn(Optional.empty());

    service().record(PAYOUT_WITHOUT_REQUEST, HOLD, ENTRY_KEY, "no matching redemption request");

    verify(notificationService).sendMessage(any(), eq(INVESTMENT));
    var saved = ArgumentCaptor.forClass(PaymentCheckEvent.class);
    verify(paymentCheckEventRepository).save(saved.capture());
    assertThat(saved.getValue().isAlertFailed()).isFalse();
  }

  @Test
  void theSameFindingSeenAgainDoesNotAlertAgain() {
    // The current day's statement is re-read every five minutes, so this is the normal case, not
    // an edge case.
    when(paymentCheckEventRepository.findByCheckTypeAndExternalKey(
            PAYOUT_WITHOUT_REQUEST, ENTRY_KEY))
        .thenReturn(Optional.of(existing(false)));

    service().record(PAYOUT_WITHOUT_REQUEST, HOLD, ENTRY_KEY, "no matching redemption request");

    verify(notificationService, never()).sendMessage(any(), any());
    verify(paymentCheckEventRepository, never()).save(any());
  }

  @Test
  void aFindingWhoseAlertFailedIsRetriedRatherThanBecomingTheNewBaseline() {
    when(paymentCheckEventRepository.findByCheckTypeAndExternalKey(
            PAYOUT_WITHOUT_REQUEST, ENTRY_KEY))
        .thenReturn(Optional.of(existing(true)));

    service().record(PAYOUT_WITHOUT_REQUEST, HOLD, ENTRY_KEY, "no matching redemption request");

    verify(notificationService, times(1)).sendMessage(any(), eq(INVESTMENT));
  }

  @Test
  void anUndeliverableAlertIsRememberedAsUndelivered() {
    when(paymentCheckEventRepository.findByCheckTypeAndExternalKey(
            PAYOUT_WITHOUT_REQUEST, ENTRY_KEY))
        .thenReturn(Optional.empty());
    doThrow(new RuntimeException("slack is down"))
        .when(notificationService)
        .sendMessage(any(), any());

    service().record(PAYOUT_WITHOUT_REQUEST, HOLD, ENTRY_KEY, "no matching redemption request");

    var saved = ArgumentCaptor.forClass(PaymentCheckEvent.class);
    verify(paymentCheckEventRepository).save(saved.capture());
    assertThat(saved.getValue().isAlertFailed()).isTrue();
  }

  private static PaymentCheckEvent existing(boolean alertFailed) {
    return PaymentCheckEvent.builder()
        .checkType(PAYOUT_WITHOUT_REQUEST)
        .severity(HOLD)
        .externalKey(ENTRY_KEY)
        .detail("no matching redemption request")
        .alertFailed(alertFailed)
        .build();
  }
}
