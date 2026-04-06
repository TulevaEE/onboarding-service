package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class TrackingDifferenceNotifier {

  private static final int ESCALATION_THRESHOLD = 3;
  private static final BigDecimal HUNDRED = new BigDecimal("100");

  private final OperationsNotificationService notificationService;

  void notify(List<TrackingDifferenceResult> results) {
    try {
      var hasAnyBreaches = results.stream().anyMatch(TrackingDifferenceResult::breach);

      if (!hasAnyBreaches) {
        notificationService.sendMessage(
            "Tracking difference check completed: all funds within limits", INVESTMENT);
        return;
      }

      var message = new StringBuilder("TD BREACH DETECTED\n");
      var hasEscalation = false;

      for (var result : results) {
        if (!result.breach()) {
          continue;
        }

        if (isEscalation(result)) {
          hasEscalation = true;
        }

        message.append(formatBreach(result));
      }

      if (hasEscalation) {
        message.insert(
            0, "TD ESCALATION — %d+ CONSECUTIVE BREACH DAYS\n".formatted(ESCALATION_THRESHOLD));
      }

      notificationService.sendMessage(message.toString(), INVESTMENT);
    } catch (Exception e) {
      log.error("Failed to send tracking difference notification", e);
    }
  }

  private String formatBreach(TrackingDifferenceResult result) {
    var sb = new StringBuilder();
    sb.append(
        "\n[%s] %s %s: TD=%s%% (fund=%s%%, benchmark=%s%%)"
            .formatted(
                result.fund(),
                result.checkType(),
                result.checkDate(),
                formatPercent(result.trackingDifference()),
                formatPercent(result.fundReturn()),
                formatPercent(result.benchmarkReturn())));

    if (!result.securityAttributions().isEmpty()) {
      var topContributors =
          result.securityAttributions().stream()
              .sorted(
                  Comparator.comparing(
                      (SecurityAttribution a) -> a.contribution().abs(), Comparator.reverseOrder()))
              .limit(3)
              .toList();

      sb.append("\n  Top: ");
      for (int i = 0; i < topContributors.size(); i++) {
        var attr = topContributors.get(i);
        if (i > 0) sb.append(", ");
        sb.append("%s (%s%%)".formatted(attr.isin(), formatPercent(attr.contribution())));
      }

      if (result.cashDrag().signum() != 0) {
        sb.append(", cash drag (%s%%)".formatted(formatPercent(result.cashDrag())));
      }
    }

    if (isEscalation(result)) {
      sb.append(" [%d consecutive days]".formatted(result.consecutiveBreachDays()));
    }

    return sb.toString();
  }

  private boolean isEscalation(TrackingDifferenceResult result) {
    return result.consecutiveBreachDays() >= ESCALATION_THRESHOLD
        && result.consecutiveNetTd() != null
        && result.consecutiveNetTd().abs().compareTo(TrackingDifferenceCalculator.BREACH_THRESHOLD)
            >= 0;
  }

  private String formatPercent(BigDecimal value) {
    var percent = value.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
    return (percent.signum() > 0 ? "+" : "") + percent.toPlainString();
  }
}
