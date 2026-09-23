package ee.tuleva.onboarding.investment.fees.ocf;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static java.math.RoundingMode.HALF_UP;
import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
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
      notificationService.sendMessage(message(run, scope, outcomes), INVESTMENT);
    } catch (Exception e) {
      log.error("Failed to send OCF run notification: run={}, scope={}", run, scope, e);
    }
  }

  private static String message(String run, String scope, List<OcfRunOutcome> outcomes) {
    return outcomes.stream()
        .map(OcfNotifier::line)
        .collect(joining("\n  ", header(run, scope, outcomes) + "\n  ", ""));
  }

  private static String header(String run, String scope, List<OcfRunOutcome> outcomes) {
    var failed = outcomes.stream().filter(OcfRunOutcome::failed).count();
    var incomplete = outcomes.stream().filter(OcfRunOutcome::incomplete).count();
    if (failed == outcomes.size()) {
      return """
             🛑 %s DID NOT PRODUCE A SINGLE FIGURE: %s
               Every fund failed, so no OCF was written for this period and the last figure on this
               channel is not this period's. Rerun it once the cause is fixed."""
          .formatted(run, scope);
    }
    if (failed > 0) {
      return """
             ⚠️ %s RAN ONLY IN PART: %s, %d of %d funds failed
               Nothing here says what those funds' OCF is this period. The rest were written."""
          .formatted(run, scope, failed, outcomes.size());
    }
    if (incomplete > 0) {
      return """
             ⚠️ %s WROTE AN INCOMPLETE FIGURE: %s, %d of %d funds have gaps
               A component resolved to zero instead of failing, so those totals are understated and
               must not be published until the gap is closed."""
          .formatted(run, scope, incomplete, outcomes.size());
    }
    return "✅ %s COMPLETE: %s".formatted(run, scope);
  }

  private static String line(OcfRunOutcome outcome) {
    var subject = "%s %s".formatted(outcome.fund().getCode(), outcome.month());
    var snapshot = outcome.snapshot();
    if (snapshot == null) {
      return "🛑 %s: %s".formatted(subject, outcome.failureReason());
    }
    if (outcome.incomplete()) {
      return "⚠️ %s: %s%%, incomplete — %s"
          .formatted(subject, formatPercent(snapshot.totalOcf()), gapNames(outcome.gaps()));
    }
    return "✅ %s: %s%%".formatted(subject, formatPercent(snapshot.totalOcf()));
  }

  private static String gapNames(List<OcfGap> gaps) {
    return gaps.stream().map(Enum::name).collect(joining(", "));
  }

  private static String formatPercent(BigDecimal rate) {
    return rate.multiply(HUNDRED).setScale(2, HALF_UP).toPlainString();
  }
}
