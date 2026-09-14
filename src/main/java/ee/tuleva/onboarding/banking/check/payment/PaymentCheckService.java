package ee.tuleva.onboarding.banking.check.payment;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static java.util.Objects.requireNonNull;
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
    doRecord(checkType, severity, externalKey, detail);
  }

  @Transactional(propagation = REQUIRES_NEW)
  public void recordStoppedPayment(PaymentCheckType checkType, String externalKey, String detail) {
    doRecord(checkType, PaymentCheckSeverity.HOLD, externalKey, detail);
  }

  private void doRecord(
      PaymentCheckType checkType,
      PaymentCheckSeverity severity,
      String externalKey,
      String detail) {
    var now = Instant.now(clock);
    var existing =
        paymentCheckEventRepository.findByCheckTypeAndExternalKey(checkType, externalKey);
    if (existing.isPresent() && !existing.get().isAlertFailed()) {
      seeAgain(existing.get(), now);
      return;
    }

    var event =
        existing.orElseGet(
            () ->
                PaymentCheckEvent.builder()
                    .checkType(checkType)
                    .externalKey(externalKey)
                    .createdAt(now)
                    .build());
    event.setSeverity(severity);
    event.setDetail(detail);
    event.setLastSeenAt(now);
    event.setAlertFailed(false);
    var saved = paymentCheckEventRepository.save(event);

    eventPublisher.publishEvent(
        new PaymentCheckRecorded(requireNonNull(saved.getId()), checkType, severity, detail));
  }

  private void seeAgain(PaymentCheckEvent event, Instant now) {
    event.setLastSeenAt(now);
    paymentCheckEventRepository.save(event);
  }

  public List<PaymentCheckEvent> holdsOn(LocalDate date) {
    var dayStart = date.atStartOfDay(TALLINN);
    return paymentCheckEventRepository
        .findBySeverityAndLastSeenAtGreaterThanEqualAndLastSeenAtLessThan(
            PaymentCheckSeverity.HOLD, dayStart.toInstant(), dayStart.plusDays(1).toInstant());
  }

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
      paymentCheckEventRepository.markAlertFailed(recorded.eventId());
    }
  }

  private static String icon(PaymentCheckSeverity severity) {
    return severity == PaymentCheckSeverity.HOLD ? "🔴" : "🟠";
  }
}
