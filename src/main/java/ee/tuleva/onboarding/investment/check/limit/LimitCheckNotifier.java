package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.OK;
import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.SOFT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class LimitCheckNotifier {

  private final OperationsNotificationService notificationService;

  void notify(List<LimitCheckResult> results) {
    try {
      if (sendAllClearIfNoBreaches(results)) {
        return;
      }
      sendBreachNotification(results);
    } catch (Exception e) {
      log.error("Failed to send limit check notification", e);
    }
  }

  private boolean sendAllClearIfNoBreaches(List<LimitCheckResult> results) {
    var hasAnyBreaches = results.stream().anyMatch(LimitCheckResult::hasBreaches);
    if (hasAnyBreaches) {
      return false;
    }
    if (results.isEmpty()) {
      return true;
    }
    var fundNames = results.stream().map(r -> r.fund().getCode()).collect(Collectors.joining(", "));
    notificationService.sendMessage(
        "✅ Limit check completed: %s within limits".formatted(fundNames), INVESTMENT);
    return true;
  }

  private void sendBreachNotification(List<LimitCheckResult> results) {
    var body = new StringBuilder();
    var worst = SOFT;

    for (var result : results) {
      worst = worse(worst, appendResultBreaches(body, result));
    }

    var message = "%s LIMIT BREACH DETECTED\n".formatted(severityIcon(worst)) + body;
    notificationService.sendMessage(message, INVESTMENT);
  }

  private BreachSeverity appendResultBreaches(StringBuilder body, LimitCheckResult result) {
    if (!result.hasBreaches()) {
      return OK;
    }
    var worst = OK;
    worst = worse(worst, appendPositionBreaches(body, result));
    worst = worse(worst, appendProviderBreaches(body, result));
    worst = worse(worst, appendReserveBreach(body, result));
    return worst;
  }

  private BreachSeverity appendPositionBreaches(StringBuilder body, LimitCheckResult result) {
    var worst = OK;
    for (var breach : result.positionBreaches()) {
      if (breach.severity() != OK) {
        worst = worse(worst, breach.severity());
        body.append(
            "\n%s [%s] POSITION %s: %s=%s%%, soft=%s%%, hard=%s%%"
                .formatted(
                    severityIcon(breach.severity()),
                    breach.severity(),
                    result.fund(),
                    breach.label(),
                    breach.actualPercent(),
                    breach.softLimitPercent(),
                    breach.hardLimitPercent()));
      }
    }
    return worst;
  }

  private BreachSeverity appendProviderBreaches(StringBuilder body, LimitCheckResult result) {
    var worst = OK;
    for (var breach : result.providerBreaches()) {
      if (breach.severity() != OK) {
        worst = worse(worst, breach.severity());
        body.append(
            "\n%s [%s] PROVIDER %s: %s=%s%%, soft=%s%%, hard=%s%%"
                .formatted(
                    severityIcon(breach.severity()),
                    breach.severity(),
                    result.fund(),
                    breach.provider(),
                    breach.actualPercent(),
                    breach.softLimitPercent(),
                    breach.hardLimitPercent()));
      }
    }
    return worst;
  }

  private BreachSeverity appendReserveBreach(StringBuilder body, LimitCheckResult result) {
    if (result.reserveBreach() == null || result.reserveBreach().severity() == OK) {
      return OK;
    }
    var breach = result.reserveBreach();
    body.append(
        "\n%s [%s] RESERVE %s: cash=%s, soft=%s, hard=%s"
            .formatted(
                severityIcon(breach.severity()),
                breach.severity(),
                result.fund(),
                breach.cashBalance(),
                breach.reserveSoft(),
                breach.reserveHard()));
    return breach.severity();
  }

  private BreachSeverity worse(BreachSeverity a, BreachSeverity b) {
    return a.compareTo(b) >= 0 ? a : b;
  }

  private String severityIcon(BreachSeverity severity) {
    return switch (severity) {
      case HARD -> "🛑";
      case SOFT -> "⚠️";
      case OK -> "✅";
    };
  }
}
