package ee.tuleva.onboarding.banking.payment;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@NullMarked
public class PaymentIntegrityNotifier {

  private final OperationsNotificationService notificationService;
  private final Clock clock;
  private final Duration cooldown;
  private final Map<String, Instant> lastAlertedAt = new ConcurrentHashMap<>();

  PaymentIntegrityNotifier(
      OperationsNotificationService notificationService,
      Clock clock,
      @Value("${banking.payment.integrity-alert-cooldown:PT1H}") Duration cooldown) {
    this.notificationService = notificationService;
    this.clock = clock;
    this.cooldown = cooldown;
  }

  @EventListener
  public void onPaymentBlocked(PaymentBlockedEvent event) {
    var checks = event.violations().stream().map(PaymentIntegrityViolation::summary).toList();
    send(
        cooldownKey("blocked", event.paymentRequest(), checks),
        "🔴 Payment BLOCKED before sending to SEB — the generated file does not match the payment request. checks=%s, amount=%s EUR, remitter=%s, beneficiary=%s <!channel>"
            .formatted(
                checks,
                event.paymentRequest().amount(),
                mask(event.paymentRequest().remitterIban()),
                mask(event.paymentRequest().beneficiaryIban())));
  }

  @EventListener
  public void onPaymentMisrouted(PaymentMisroutedEvent event) {
    send(
        cooldownKey("misrouted", event.paymentRequest(), List.of()),
        "🔴 Payment NOT SENT — remitter is not a known SEB account, so no bank received it. amount=%s EUR, remitter=%s <!channel>"
            .formatted(
                event.paymentRequest().amount(), mask(event.paymentRequest().remitterIban())));
  }

  // The subscription batch job retries every minute, so an unfixed generator bug would otherwise
  // repost the same alert 60 times an hour until someone deploys a fix. The key is the payment's
  // own
  // shape rather than the violated checks alone, so a retry of the same batch is suppressed while a
  // DIFFERENT payment failing the same check still alerts. endToEndId would not work as the key:
  // the
  // retrying batch mints a fresh UUID every minute.
  private static String cooldownKey(
      String kind, PaymentRequest paymentRequest, List<String> checks) {
    return String.join(
        "|",
        kind,
        mask(paymentRequest.remitterIban()),
        mask(paymentRequest.beneficiaryIban()),
        String.valueOf(paymentRequest.amount()),
        checks.toString());
  }

  private void send(String key, String message) {
    var now = Instant.now(clock);
    var previous = lastAlertedAt.get(key);
    if (previous != null && previous.plus(cooldown).isAfter(now)) {
      return;
    }
    lastAlertedAt.entrySet().removeIf(entry -> entry.getValue().plus(cooldown).isBefore(now));
    lastAlertedAt.put(key, now);
    try {
      notificationService.sendMessage(message, SAVINGS);
    } catch (Exception e) {
      lastAlertedAt.remove(key);
      log.error("Failed to send payment integrity notification", e);
    }
  }

  private static String mask(String iban) {
    return iban.length() <= 4 ? "…" : "…" + iban.substring(iban.length() - 4);
  }
}
