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
      var hasAnyBreaches = results.stream().anyMatch(LimitCheckResult::hasBreaches);

      if (!hasAnyBreaches) {
        if (results.isEmpty()) {
          return;
        }
        var fundNames =
            results.stream().map(r -> r.fund().getCode()).collect(Collectors.joining(", "));
        notificationService.sendMessage(
            "✅ Limit check completed: %s within limits".formatted(fundNames), INVESTMENT);
        return;
      }

      var body = new StringBuilder();
      var worst = SOFT;

      for (var result : results) {
        if (!result.hasBreaches()) {
          continue;
        }

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

        if (result.reserveBreach() != null && result.reserveBreach().severity() != OK) {
          var breach = result.reserveBreach();
          worst = worse(worst, breach.severity());
          body.append(
              "\n%s [%s] RESERVE %s: cash=%s, soft=%s, hard=%s"
                  .formatted(
                      severityIcon(breach.severity()),
                      breach.severity(),
                      result.fund(),
                      breach.cashBalance(),
                      breach.reserveSoft(),
                      breach.reserveHard()));
        }
      }

      var message = "%s LIMIT BREACH DETECTED\n".formatted(severityIcon(worst)) + body;
      notificationService.sendMessage(message, INVESTMENT);

    } catch (Exception e) {
      log.error("Failed to send limit check notification", e);
    }
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
