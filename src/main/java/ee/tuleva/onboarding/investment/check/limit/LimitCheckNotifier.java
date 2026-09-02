package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.OK;
import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.SOFT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class LimitCheckNotifier {

  private final OperationsNotificationService notificationService;

  void notify(LimitCheckRun run) {
    try {
      if (run.hasBreaches()) {
        sendBreachNotification(run);
        return;
      }
      sendAllClear(run);
    } catch (Exception e) {
      log.error("Failed to send limit check notification", e);
    }
  }

  void notifyBackfillFailed(Exception failure) {
    try {
      notificationService.sendMessage(
          "🛑 Limit check backfill FAILED — the daily re-run that repairs missed days did not"
              + " complete, so gaps will persist until it succeeds: error=%s"
                  .formatted(failure.getMessage()),
          INVESTMENT);
    } catch (Exception e) {
      log.error("Failed to send limit check backfill failure notification", e);
    }
  }

  // A run that checked nothing at all used to say nothing at all, which is indistinguishable from
  // a quiet day. Whatever a run did or did not cover, it now says so.
  private void sendAllClear(LimitCheckRun run) {
    if (run.isEmpty()) {
      notificationService.sendMessage(
          "⏸ Limit check ran but covered no funds — no position data for any of them", INVESTMENT);
      return;
    }
    var message = new StringBuilder();
    if (!run.results().isEmpty()) {
      var fundNames =
          run.results().stream().map(r -> r.fund().getCode()).collect(Collectors.joining(", "));
      message.append("✅ Limit check completed: %s within limits".formatted(fundNames));
    }
    appendNotChecked(message, run);
    notificationService.sendMessage(message.toString(), INVESTMENT);
  }

  private void sendBreachNotification(LimitCheckRun run) {
    var body = new StringBuilder();
    var worst = SOFT;

    for (var result : run.results()) {
      worst = worse(worst, appendResultBreaches(body, result));
    }

    var message =
        new StringBuilder("%s LIMIT BREACH DETECTED\n".formatted(severityIcon(worst))).append(body);
    appendNotChecked(message, run);
    notificationService.sendMessage(message.toString(), INVESTMENT);
  }

  private void appendNotChecked(StringBuilder message, LimitCheckRun run) {
    if (run.notChecked().isEmpty()) {
      return;
    }
    var fundNames =
        run.notChecked().stream().map(TulevaFund::getCode).collect(Collectors.joining(", "));
    message
        .append(message.isEmpty() ? "" : "\n\n")
        .append("⏸ Not checked: %s — no limits were verified for these".formatted(fundNames));
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
