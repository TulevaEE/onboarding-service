package ee.tuleva.onboarding.banking.check.payment;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;
import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Records a payment finding once and alerts on it once.
 *
 * <p>The dedupe is persisted rather than held in memory on purpose: the current day's statement is
 * re-fetched every five minutes, so a detector that simply fires on each read would re-alert on the
 * same entry all afternoon and teach the reader to ignore the channel.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCheckService {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  private final PaymentCheckEventRepository paymentCheckEventRepository;
  private final OperationsNotificationService notificationService;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  @Transactional
  public void record(
      PaymentCheckType checkType,
      PaymentCheckSeverity severity,
      String externalKey,
      String detail) {
    var existing =
        paymentCheckEventRepository.findByCheckTypeAndExternalKey(checkType, externalKey);
    if (existing.isPresent() && !existing.get().isAlertFailed()) {
      return;
    }

    var event =
        existing.orElseGet(
            () ->
                PaymentCheckEvent.builder().checkType(checkType).externalKey(externalKey).build());
    event.setSeverity(severity);
    event.setDetail(detail);
    event.setCreatedAt(Instant.now(clock));
    // Assume the alert will go out; the listener corrects this if it cannot.
    event.setAlertFailed(false);
    var saved = paymentCheckEventRepository.save(event);

    // The alert is published rather than sent, so it goes out only once this write commits. A
    // detector firing inside a transaction that later rolls back would otherwise announce something
    // that did not happen -- and the row recording it would be gone, so the dedupe would be lost
    // too and it would announce it again next time.
    eventPublisher.publishEvent(
        new PaymentCheckRecorded(saved.getId(), checkType, severity, detail));
  }

  public List<PaymentCheckEvent> holdsOn(LocalDate date) {
    var dayStart = date.atStartOfDay(TALLINN).toInstant();
    return paymentCheckEventRepository.findBySeverityAndCreatedAtBetween(
        PaymentCheckSeverity.HOLD, dayStart, dayStart.plus(java.time.Duration.ofDays(1)));
  }

  /**
   * Sent only once the finding itself has committed. A detector firing inside a transaction that
   * later rolls back would otherwise announce something that did not happen — and the row recording
   * it would be gone, so the dedupe would be lost too and it would announce it again next time.
   */
  @TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)
  public void alert(PaymentCheckRecorded recorded) {
    if (recorded.severity() == PaymentCheckSeverity.INFO) {
      return;
    }
    try {
      notificationService.sendMessage(
          "%s %s — %s"
              .formatted(icon(recorded.severity()), recorded.checkType(), recorded.detail()),
          INVESTMENT);
    } catch (Exception e) {
      log.error("Failed to send payment check notification: checkType={}", recorded.checkType(), e);
      markAlertFailed(recorded.eventId());
    }
  }

  /**
   * So a finding first seen during a chat outage alerts again rather than becoming the baseline.
   */
  @Transactional(propagation = REQUIRES_NEW)
  public void markAlertFailed(Long eventId) {
    paymentCheckEventRepository
        .findById(eventId)
        .ifPresent(
            event -> {
              event.setAlertFailed(true);
              paymentCheckEventRepository.save(event);
            });
  }

  private static String icon(PaymentCheckSeverity severity) {
    return severity == PaymentCheckSeverity.HOLD ? "🔴" : "🟠";
  }
}
