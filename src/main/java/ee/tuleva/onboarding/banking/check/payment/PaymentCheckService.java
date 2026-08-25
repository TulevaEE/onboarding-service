package ee.tuleva.onboarding.banking.check.payment;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    event.setAlertFailed(!alert(checkType, severity, detail));
    paymentCheckEventRepository.save(event);
  }

  public List<PaymentCheckEvent> holdsOn(LocalDate date) {
    var dayStart = date.atStartOfDay(TALLINN).toInstant();
    return paymentCheckEventRepository.findBySeverityAndCreatedAtBetween(
        PaymentCheckSeverity.HOLD, dayStart, dayStart.plus(java.time.Duration.ofDays(1)));
  }

  private boolean alert(PaymentCheckType checkType, PaymentCheckSeverity severity, String detail) {
    if (severity == PaymentCheckSeverity.INFO) {
      return true;
    }
    try {
      notificationService.sendMessage(
          "%s %s — %s".formatted(icon(severity), checkType, detail), INVESTMENT);
      return true;
    } catch (Exception e) {
      log.error("Failed to send payment check notification: checkType={}", checkType, e);
      return false;
    }
  }

  private static String icon(PaymentCheckSeverity severity) {
    return severity == PaymentCheckSeverity.HOLD ? "🔴" : "🟠";
  }
}
