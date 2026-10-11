package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.investment.check.limit.OwnershipResultLines.NOT_VERIFIED_ICON;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.INFO;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING;
import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.investment.check.limit.OwnershipCheckRun.NotChecked;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.notification.OperationsNotificationService.Severity;
import java.time.YearMonth;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class OwnershipLimitCheckNotifier {

  private static final String LINE_INDENT = "  ";

  private final OperationsNotificationService notificationService;

  void notify(OwnershipCheckRun run) {
    try {
      notificationService.sendMessage(message(run), INVESTMENT, severity(run));
    } catch (Exception e) {
      log.error("Failed to send ownership limit check notification: month={}", run.month(), e);
    }
  }

  void notifyFailed(YearMonth month, Exception failure) {
    try {
      notificationService.sendMessage(
          "🛑 Ownership limit check FAILED: month=%s, error=%s — no underlying fund was checked"
              .formatted(month, OwnershipLimitCheckService.describe(failure)),
          INVESTMENT,
          ERROR);
    } catch (Exception e) {
      log.error("Failed to send ownership limit check failure notification: month={}", month, e);
    }
  }

  private static Severity severity(OwnershipCheckRun run) {
    if (run.leftAFundWhollyUnchecked()) {
      return ERROR;
    }
    return switch (run.worstSeverity()) {
      case HARD -> ERROR;
      case SOFT -> WARNING;
      case OK -> run.hasStaleFundSizes() || run.leftFundSizesUncompared() ? WARNING : INFO;
    };
  }

  private static String message(OwnershipCheckRun run) {
    if (run.results().isEmpty() && run.fundsNotChecked().isEmpty()) {
      return "%s Ownership limit check covered no fund: month=%s — no fund has an ownership limit"
          .formatted(NOT_VERIFIED_ICON, run.month());
    }
    return Stream.concat(
            Stream.of(headline(run)),
            Stream.concat(
                    run.results().stream().flatMap(OwnershipResultLines::of),
                    run.fundsNotChecked().stream().map(OwnershipLimitCheckNotifier::notCheckedLine))
                .map(line -> LINE_INDENT + line))
        .collect(joining("\n"));
  }

  private static String headline(OwnershipCheckRun run) {
    return switch (run.worstSeverity()) {
      case HARD -> "🛑 OWNERSHIP LIMIT BREACH: month=%s".formatted(run.month());
      case SOFT -> "⚠️ OWNERSHIP SOFT LIMIT EXCEEDED: month=%s".formatted(run.month());
      case OK ->
          !run.coveredEveryHolding()
              ? "%s Ownership limit check INCOMPLETE: month=%s"
                  .formatted(NOT_VERIFIED_ICON, run.month())
              : run.hasStaleFundSizes()
                  ? "⚠️ Ownership limit check OK, but EODHD fund sizes look stale: month=%s"
                      .formatted(run.month())
                  : run.leftFundSizesUncompared()
                      ? ("⚠️ Ownership limit check OK, but EODHD fund sizes were not compared with"
                              + " the month before: month=%s")
                          .formatted(run.month())
                      : "✅ Ownership limit check OK: month=%s".formatted(run.month());
    };
  }

  private static String notCheckedLine(NotChecked notChecked) {
    return "%s Not checked: %s — %s"
        .formatted(NOT_VERIFIED_ICON, notChecked.fund().getCode(), notChecked.reason());
  }
}
