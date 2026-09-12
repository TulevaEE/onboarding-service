package ee.tuleva.onboarding.banking.check.payment;

import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckSeverity.HOLD;
import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckType.PAYOUT_WITHOUT_REQUEST;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.time.MutableClock;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class PaymentCheckServiceTest {

  private static final String ENTRY_KEY = "entry-123";
  private static final long SAVED_ID = 1L;

  @Mock private PaymentCheckEventRepository paymentCheckEventRepository;
  @Mock private OperationsNotificationService notificationService;
  @Mock private ApplicationEventPublisher eventPublisher;

  private final MutableClock clock = new MutableClock();

  private PaymentCheckService service() {
    return new PaymentCheckService(
        paymentCheckEventRepository, notificationService, eventPublisher, clock);
  }

  @Test
  void aNewFindingIsRecorded() {
    when(paymentCheckEventRepository.findByCheckTypeAndExternalKey(
            PAYOUT_WITHOUT_REQUEST, ENTRY_KEY))
        .thenReturn(Optional.empty());
    savingAssignsAnId();

    service().record(PAYOUT_WITHOUT_REQUEST, HOLD, ENTRY_KEY, "no matching redemption request");

    var saved = ArgumentCaptor.forClass(PaymentCheckEvent.class);
    verify(paymentCheckEventRepository).save(saved.capture());
    assertThat(saved.getValue().isAlertFailed()).isFalse();
  }

  @Test
  void theAlertIsNotSentUntilTheFindingItselfHasCommitted() {
    // A detector firing inside a transaction that later rolls back would otherwise announce
    // something that did not happen -- and the row recording it would be gone, so the dedupe would
    // be lost too and it would announce it again next time.
    when(paymentCheckEventRepository.findByCheckTypeAndExternalKey(
            PAYOUT_WITHOUT_REQUEST, ENTRY_KEY))
        .thenReturn(Optional.empty());
    savingAssignsAnId();

    service().record(PAYOUT_WITHOUT_REQUEST, HOLD, ENTRY_KEY, "no matching redemption request");

    verify(notificationService, never()).sendMessage(any(), any());
    verify(eventPublisher).publishEvent(any(PaymentCheckRecorded.class));
  }

  @Test
  void theSameFindingSeenAgainDoesNotAlertAgain() {
    // The current day's statement is re-read every five minutes, so this is the normal case, not
    // an edge case.
    when(paymentCheckEventRepository.findByCheckTypeAndExternalKey(
            PAYOUT_WITHOUT_REQUEST, ENTRY_KEY))
        .thenReturn(Optional.of(existing(false)));

    service().record(PAYOUT_WITHOUT_REQUEST, HOLD, ENTRY_KEY, "no matching redemption request");

    verifyNoInteractions(eventPublisher);
    verify(paymentCheckEventRepository, never()).save(any());
  }

  @Test
  void aFindingWhoseAlertFailedIsRetriedRatherThanBecomingTheNewBaseline() {
    when(paymentCheckEventRepository.findByCheckTypeAndExternalKey(
            PAYOUT_WITHOUT_REQUEST, ENTRY_KEY))
        .thenReturn(Optional.of(existing(true)));
    savingAssignsAnId();

    service().record(PAYOUT_WITHOUT_REQUEST, HOLD, ENTRY_KEY, "no matching redemption request");

    verify(eventPublisher).publishEvent(any(PaymentCheckRecorded.class));
  }

  @Test
  void alertingSendsTheFindingToTheOpsChannel() {
    service().alert(new PaymentCheckRecorded(1L, PAYOUT_WITHOUT_REQUEST, HOLD, "detail"));

    verify(notificationService).sendMessage(any(), eq(INVESTMENT));
  }

  @Test
  void anInfoFindingIsRecordedWithoutBotheringAnyone() {
    service()
        .alert(
            new PaymentCheckRecorded(1L, PAYOUT_WITHOUT_REQUEST, PaymentCheckSeverity.INFO, "d"));

    verifyNoInteractions(notificationService);
  }

  @Test
  void anUndeliverableAlertIsRememberedAsUndelivered() {
    var event = existing(false);
    event.setId(1L);
    when(paymentCheckEventRepository.findById(1L)).thenReturn(Optional.of(event));
    doThrow(new RuntimeException("chat is down"))
        .when(notificationService)
        .sendMessage(any(), any());

    service().alert(new PaymentCheckRecorded(1L, PAYOUT_WITHOUT_REQUEST, HOLD, "detail"));

    var saved = ArgumentCaptor.forClass(PaymentCheckEvent.class);
    verify(paymentCheckEventRepository).save(saved.capture());
    assertThat(saved.getValue().isAlertFailed()).isTrue();
  }

  /**
   * Saving assigns the id, and the alert carries it — so a stub returning the argument unchanged
   * would be a row that was never persisted.
   */
  private void savingAssignsAnId() {
    when(paymentCheckEventRepository.save(any()))
        .thenAnswer(
            call -> {
              PaymentCheckEvent event = call.getArgument(0);
              event.setId(SAVED_ID);
              return event;
            });
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
