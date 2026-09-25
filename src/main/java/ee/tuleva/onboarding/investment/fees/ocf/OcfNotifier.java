package ee.tuleva.onboarding.investment.fees.ocf;

import static ee.tuleva.onboarding.investment.fees.ocf.OcfNotifier.OcfRunKind.BACKFILL;
import static ee.tuleva.onboarding.investment.fees.ocf.OcfNotifier.OcfRunKind.RUN;
import static ee.tuleva.onboarding.investment.fees.ocf.OcfNotifier.OutcomeStatus.COMPLETE;
import static ee.tuleva.onboarding.investment.fees.ocf.OcfNotifier.OutcomeStatus.FAILED;
import static ee.tuleva.onboarding.investment.fees.ocf.OcfNotifier.OutcomeStatus.INCOMPLETE;
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
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class OcfNotifier {

  private static final BigDecimal HUNDRED = new BigDecimal("100");
  private static final int PERCENT_DECIMAL_PLACES = 2;
  private static final String OUTCOME_LINE_INDENT = "  ";

  private final OperationsNotificationService notificationService;

  void notifyRun(YearMonth month, List<OcfRunOutcome> outcomes) {
    send(RUN, "month=%s".formatted(month), outcomes);
  }

  void notifyBackfill(int monthsBack, List<OcfRunOutcome> outcomes) {
    send(BACKFILL, "monthsBack=%d".formatted(monthsBack), outcomes);
  }

  private void send(OcfRunKind kind, String scope, List<OcfRunOutcome> outcomes) {
    if (outcomes.isEmpty()) {
      log.warn("Nothing to report about an OCF run: run={}, scope={}", kind.label, scope);
      return;
    }
    try {
      notificationService.sendMessage(
          message(kind, scope, outcomes), INVESTMENT, severity(outcomes));
    } catch (Exception e) {
      log.error("Failed to send OCF run notification: run={}, scope={}", kind.label, scope, e);
    }
  }

  private static Severity severity(List<OcfRunOutcome> outcomes) {
    return outcomes.stream().allMatch(outcome -> status(outcome) == COMPLETE) ? INFO : ERROR;
  }

  private static String message(OcfRunKind kind, String scope, List<OcfRunOutcome> outcomes) {
    return Stream.concat(
            summary(kind, scope, outcomes),
            outcomes.stream().map(outcome -> OUTCOME_LINE_INDENT + line(outcome)))
        .collect(joining("\n"));
  }

  private static Stream<String> summary(
      OcfRunKind kind, String scope, List<OcfRunOutcome> outcomes) {
    long failed = countWith(FAILED, outcomes);
    long incomplete = countWith(INCOMPLETE, outcomes);
    if (failed == outcomes.size()) {
      return Stream.of(nothingProduced(kind, scope, outcomes));
    }
    if (failed > 0) {
      return Stream.concat(
          Stream.of(ranOnlyInPart(kind, scope, failed, outcomes)), gapWarnings(incomplete));
    }
    if (incomplete > 0) {
      return Stream.of(wroteIncompleteFigures(kind, scope, incomplete, outcomes));
    }
    return Stream.of("%s %s COMPLETE: %s".formatted(COMPLETE.icon, kind.label, scope));
  }

  private static String nothingProduced(
      OcfRunKind kind, String scope, List<OcfRunOutcome> outcomes) {
    return """
        %s %s DID NOT PRODUCE A SINGLE FIGURE: %s
          Every one of the %d %s failed, so no OCF was written for this period and the last
          figure on this channel is not this period's. Rerun it once the cause is fixed."""
        .formatted(FAILED.icon, kind.label, scope, outcomes.size(), countedUnit(outcomes));
  }

  private static String ranOnlyInPart(
      OcfRunKind kind, String scope, long failed, List<OcfRunOutcome> outcomes) {
    return """
        %s %s RAN ONLY IN PART: %s, %d of %d %s failed
          Nothing here says what their OCF is this period. The rest were written."""
        .formatted(
            INCOMPLETE.icon, kind.label, scope, failed, outcomes.size(), countedUnit(outcomes));
  }

  private static Stream<String> gapWarnings(long incomplete) {
    if (incomplete == 0) {
      return Stream.empty();
    }
    return Stream.of(
        """
        %s %d of the written ones has a gap: a component resolved to zero instead of failing,
          so that total is understated and must not be published until the gap is closed."""
            .formatted(INCOMPLETE.icon, incomplete));
  }

  private static String wroteIncompleteFigures(
      OcfRunKind kind, String scope, long incomplete, List<OcfRunOutcome> outcomes) {
    return """
        %s %s WROTE AN INCOMPLETE FIGURE: %s, %d of %d %s have gaps
          A component resolved to zero instead of failing, so those totals are understated and
          must not be published until the gap is closed."""
        .formatted(
            INCOMPLETE.icon, kind.label, scope, incomplete, outcomes.size(), countedUnit(outcomes));
  }

  private static long countWith(OutcomeStatus status, List<OcfRunOutcome> outcomes) {
    return outcomes.stream().filter(outcome -> status(outcome) == status).count();
  }

  private static String countedUnit(List<OcfRunOutcome> outcomes) {
    return outcomes.stream().map(OcfRunOutcome::month).distinct().count() > 1
        ? "fund-months"
        : "funds";
  }

  private static OutcomeStatus status(OcfRunOutcome outcome) {
    return switch (outcome) {
      case Failed _ -> FAILED;
      case Computed computed when computed.incomplete() -> INCOMPLETE;
      case Computed _ -> COMPLETE;
    };
  }

  private static String line(OcfRunOutcome outcome) {
    return "%s %s %s: %s"
        .formatted(
            status(outcome).icon, outcome.fund().getCode(), outcome.month(), detail(outcome));
  }

  private static String detail(OcfRunOutcome outcome) {
    return switch (outcome) {
      case Failed failed -> failed.reason();
      case Computed computed when computed.incomplete() ->
          "%s%%, incomplete — %s".formatted(totalOcfPercent(computed), gapNames(computed.gaps()));
      case Computed computed -> "%s%%".formatted(totalOcfPercent(computed));
    };
  }

  private static String gapNames(List<OcfGap> gaps) {
    return gaps.stream().map(Enum::name).collect(joining(", "));
  }

  private static String totalOcfPercent(Computed computed) {
    return computed
        .snapshot()
        .totalOcf()
        .multiply(HUNDRED)
        .setScale(PERCENT_DECIMAL_PLACES, HALF_UP)
        .toPlainString();
  }

  @RequiredArgsConstructor
  enum OcfRunKind {
    RUN("OCF RUN"),
    BACKFILL("OCF BACKFILL");

    private final String label;
  }

  @RequiredArgsConstructor
  enum OutcomeStatus {
    FAILED("🛑"),
    INCOMPLETE("⚠️"),
    COMPLETE("✅");

    private final String icon;
  }
}
