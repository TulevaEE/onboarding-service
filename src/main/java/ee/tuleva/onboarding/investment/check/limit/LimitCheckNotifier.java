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

  private static final int STANDING_GAP_DAYS = 5;

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

  void notifyPositionSyncFailed(Exception failure) {
    try {
      notificationService.sendMessage(
          "⚠️ Fee accrual position sync failed before the limit check gap fill — the checks below"
              + " ran against the positions already stored: error=%s"
                  .formatted(failure.getMessage()),
          INVESTMENT);
    } catch (Exception e) {
      log.error("Failed to send limit check position sync failure notification", e);
    }
  }

  private void sendAllClear(LimitCheckRun run) {
    if (run.isEmpty()) {
      notificationService.sendMessage(
          "⏸ Limit check ran but covered no funds — no position data for any of them", INVESTMENT);
      return;
    }
    var message = new StringBuilder();
    if (!run.results().isEmpty()) {
      // A gap fill returns many dates per fund, so the fund has to be named once and the dates
      // said out loud - otherwise the same four codes repeat down the message with nothing to say
      // which day any of them is about.
      var fundNames =
          run.results().stream()
              .map(r -> r.fund().getCode())
              .distinct()
              .sorted()
              .collect(Collectors.joining(", "));
      var dates =
          run.results().stream().map(LimitCheckResult::checkDate).distinct().sorted().toList();
      message.append(
          dates.size() == 1
              ? "✅ Limit check completed: %s within limits on %s"
                  .formatted(fundNames, dates.getFirst())
              : "✅ Limit check completed: %s within limits on %d dates, %s to %s"
                  .formatted(fundNames, dates.size(), dates.getFirst(), dates.getLast()));
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
    appendUnfilledGaps(message, run);
    if (run.fundsNotChecked().isEmpty()) {
      return;
    }
    var fundNames =
        run.fundsNotChecked().stream().map(TulevaFund::getCode).collect(Collectors.joining(", "));
    message
        .append(message.isEmpty() ? "" : "\n\n")
        .append("⏸ Not checked: %s — no limits were verified for these".formatted(fundNames));
  }

  // These days have no limit check and nothing else will ever mention them, so they are named in
  // full every evening. Once a gap has stood longer than a working week it is not going to fill
  // itself, so it says how long it has been open and the last evening it will be attempted before
  // it drops out of the lookback window unchecked.
  private void appendUnfilledGaps(StringBuilder message, LimitCheckRun run) {
    if (run.unfilledGaps().isEmpty()) {
      return;
    }
    message
        .append(message.isEmpty() ? "" : "\n\n")
        .append("⏸ Not checked — no limits were verified for these days:");
    run.unfilledGaps()
        .forEach(
            gap ->
                message.append(
                    "\n  %s %s%s"
                        .formatted(gap.fund().getCode(), gap.checkDate(), describeAge(gap))));
  }

  private String describeAge(LimitCheckRun.UnfilledGap gap) {
    if (gap.daysUnfilled() <= STANDING_GAP_DAYS) {
      return "";
    }
    return " — standing gap: open for %d days, last attempt %s"
        .formatted(gap.daysUnfilled(), gap.lastAttempt());
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
            "\n%s [%s] POSITION %s %s: %s=%s%%, soft=%s%%, hard=%s%%"
                .formatted(
                    severityIcon(breach.severity()),
                    breach.severity(),
                    result.fund(),
                    result.checkDate(),
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
            "\n%s [%s] PROVIDER %s %s: %s=%s%%, soft=%s%%, hard=%s%%"
                .formatted(
                    severityIcon(breach.severity()),
                    breach.severity(),
                    result.fund(),
                    result.checkDate(),
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
        "\n%s [%s] RESERVE %s %s: cash=%s, soft=%s, hard=%s"
            .formatted(
                severityIcon(breach.severity()),
                breach.severity(),
                result.fund(),
                result.checkDate(),
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
