package ee.tuleva.onboarding.investment.fees.ocf;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.INFO;
import static java.math.RoundingMode.HALF_UP;
import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.investment.fees.ocf.OcfRunOutcome.Computed;
import ee.tuleva.onboarding.investment.fees.ocf.OcfRunOutcome.Failed;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.notification.OperationsNotificationService.Severity;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class OcfNotifier {

  private static final BigDecimal HUNDRED = new BigDecimal("100");

  private final OperationsNotificationService notificationService;

  void notifyRun(YearMonth month, List<OcfRunOutcome> outcomes) {
    send("OCF RUN", "month=%s".formatted(month), outcomes);
  }

  void notifyBackfill(int monthsBack, List<OcfRunOutcome> outcomes) {
    send("OCF BACKFILL", "monthsBack=%d".formatted(monthsBack), outcomes);
  }

  private void send(String run, String scope, List<OcfRunOutcome> outcomes) {
    if (outcomes.isEmpty()) {
      log.warn("Nothing to report about an OCF run: run={}, scope={}", run, scope);
      return;
    }
    try {
      notificationService.sendMessage(
          message(run, scope, outcomes), INVESTMENT, severity(outcomes));
    } catch (Exception e) {
      log.error("Failed to send OCF run notification: run={}, scope={}", run, scope, e);
    }
  }

  private static Severity severity(List<OcfRunOutcome> outcomes) {
    return outcomes.stream().anyMatch(o -> isFailed(o) || isIncomplete(o)) ? ERROR : INFO;
  }

  private static String message(String run, String scope, List<OcfRunOutcome> outcomes) {
    return outcomes.stream()
        .map(OcfNotifier::line)
        .collect(joining("\n  ", header(run, scope, outcomes) + "\n  ", ""));
  }

  private static String header(String run, String scope, List<OcfRunOutcome> outcomes) {
    long failed = outcomes.stream().filter(OcfNotifier::isFailed).count();
    long incomplete = outcomes.stream().filter(OcfNotifier::isIncomplete).count();
    if (failed == outcomes.size()) {
      return """
             🛑 %s DID NOT PRODUCE A SINGLE FIGURE: %s
               Every one of the %d %s failed, so no OCF was written for this period and the last
               figure on this channel is not this period's. Rerun it once the cause is fixed."""
          .formatted(run, scope, outcomes.size(), unitOf(outcomes));
    }
    if (failed > 0) {
      return """
             ⚠️ %s RAN ONLY IN PART: %s, %d of %d %s failed
               Nothing here says what their OCF is this period. The rest were written.%s"""
          .formatted(run, scope, failed, outcomes.size(), unitOf(outcomes), gapClause(incomplete));
    }
    if (incomplete > 0) {
      return """
             ⚠️ %s WROTE AN INCOMPLETE FIGURE: %s, %d of %d %s have gaps
               A component resolved to zero instead of failing, so those totals are understated and
               must not be published until the gap is closed."""
          .formatted(run, scope, incomplete, outcomes.size(), unitOf(outcomes));
    }
    return "✅ %s COMPLETE: %s".formatted(run, scope);
  }

  private static String gapClause(long incomplete) {
    if (incomplete == 0) {
      return "";
    }
    return """

             ⚠️ %d of the written ones has a gap: a component resolved to zero instead of failing,
               so that total is understated and must not be published until the gap is closed."""
        .formatted(incomplete);
  }

  private static String unitOf(List<OcfRunOutcome> outcomes) {
    return outcomes.stream().map(OcfRunOutcome::month).distinct().count() > 1
        ? "fund-months"
        : "funds";
  }

  private static boolean isFailed(OcfRunOutcome outcome) {
    return outcome instanceof Failed;
  }

  private static boolean isIncomplete(OcfRunOutcome outcome) {
    return outcome instanceof Computed computed && computed.incomplete();
  }

  private static String line(OcfRunOutcome outcome) {
    var subject = "%s %s".formatted(outcome.fund().getCode(), outcome.month());
    return switch (outcome) {
      case Failed failed -> "🛑 %s: %s".formatted(subject, failed.reason());
      case Computed computed when computed.incomplete() ->
          "⚠️ %s: %s%%, incomplete — %s"
              .formatted(
                  subject,
                  formatPercent(computed.snapshot().totalOcf()),
                  gapNames(computed.gaps()));
      case Computed computed ->
          "✅ %s: %s%%".formatted(subject, formatPercent(computed.snapshot().totalOcf()));
    };
  }

  private static String gapNames(List<OcfGap> gaps) {
    return gaps.stream().map(Enum::name).collect(joining(", "));
  }

  private static String formatPercent(BigDecimal rate) {
    return rate.multiply(HUNDRED).setScale(2, HALF_UP).toPlainString();
  }
}
