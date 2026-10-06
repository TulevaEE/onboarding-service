package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.TrackingCheckType.BENCHMARK;
import static ee.tuleva.onboarding.investment.TrackingCheckType.BENCHMARK_MODEL;
import static ee.tuleva.onboarding.investment.TrackingCheckType.MODEL_PORTFOLIO;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.investment.check.tracking.EscalationRule.Verdict;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class TrackingDifferenceNotifier {

  private static final BigDecimal HUNDRED = new BigDecimal("100");
  private static final String PUBLISHED_WITHOUT_VALIDATION =
      "NAV report published WITHOUT tracking-difference validation";
  private static final String BACKFILL_COMPLETE_HEADER = "✅ TD BACKFILL COMPLETE: daysBack=%d\n";
  private static final String BACKFILL_INCOMPLETE_HEADER =
      """
      ⚠️ TD BACKFILL INCOMPLETE: daysBack=%d
        The check dates named in the message above were not re-run and keep their old \
      events, and the breach streaks counted after them still run through those events. \
      Rerun the backfill once their prices are in.
      """;

  private final OperationsNotificationService notificationService;
  private final TrackingDifferenceCalculator calculator;
  private final RedemptionCycleLookup redemptionCycleLookup;

  void notifyCheckCouldNotRun(TulevaFund fund, LocalDate navDate) {
    try {
      notificationService.sendMessage(
          "⚠️ TD CHECK DID NOT RUN: fund=%s, date=%s — missing NAV, prices, or model data; %s"
              .formatted(fund.getCode(), navDate, PUBLISHED_WITHOUT_VALIDATION),
          INVESTMENT);
    } catch (Exception e) {
      log.error("Failed to send tracking difference 'check did not run' notification", e);
    }
  }

  void notifyCheckFailed(TulevaFund fund, LocalDate navDate, String reason) {
    try {
      notificationService.sendMessage(
          "⚠️ TD CHECK FAILED: fund=%s, date=%s — the check errored (%s); %s"
              .formatted(fund.getCode(), navDate, reason, PUBLISHED_WITHOUT_VALIDATION),
          INVESTMENT);
    } catch (Exception e) {
      log.error("Failed to send tracking difference 'check failed' notification", e);
    }
  }

  void notifyRunIncomplete(String run, String reason) {
    try {
      notificationService.sendMessage(
          """
          ⚠️ %s RAN ONLY IN PART: %s
            Those funds were skipped, so nothing on this channel says whether their tracking
            difference is within limits today. The result that follows covers the rest."""
              .formatted(run, reason),
          INVESTMENT);
    } catch (Exception e) {
      log.error("Failed to send tracking difference partial run notification", e);
    }
  }

  void notifyRunFailed(String run, String reason) {
    try {
      notificationService.sendMessage(
          """
          🛑 %s DID NOT RUN: %s
            Nothing was checked and nothing was refilled, so the last message on this channel is
            not the state of the funds today. Rerun it once the cause is fixed."""
              .formatted(run, reason),
          INVESTMENT);
    } catch (Exception e) {
      log.error("Failed to send tracking difference run failure notification", e);
    }
  }

  void notifyResidualOutsideTolerance(
      TulevaFund fund,
      LocalDate periodStart,
      LocalDate periodEnd,
      BigDecimal residual,
      @Nullable BigDecimal tolerance) {
    try {
      notificationService.sendMessage(
          """
          🛑 TD ATTRIBUTION UNEXPLAINED: fund=%s, period=%s to %s
            Residual %s bps, tolerance %s bps.
            The attribution did not explain the period's tracking difference. The components it
            does report cannot be relied on until the gap is understood."""
              .formatted(
                  fund.getCode(),
                  periodStart,
                  periodEnd,
                  toBps(residual),
                  tolerance == null ? "none" : toBps(tolerance)),
          INVESTMENT);
    } catch (Exception e) {
      log.error("Failed to send tracking difference residual notification", e);
    }
  }

  void notifyAttributionNotWritten(
      TulevaFund fund, LocalDate periodStart, LocalDate periodEnd, List<LocalDate> staleDates) {
    try {
      notificationService.sendMessage(
          """
          ⚠️ TD ATTRIBUTION NOT WRITTEN: fund=%s, period=%s to %s
            The NAV of %s changed after the check ran and the recheck did not
            complete, so the stored fund return of those dates is stale. Any attribution already
            stored for this period is left as it was. Rerun it once those dates recheck; the
            reason per date is in the logs."""
              .formatted(
                  fund.getCode(),
                  periodStart,
                  periodEnd,
                  staleDates.stream().map(LocalDate::toString).collect(joining(", "))),
          INVESTMENT);
    } catch (Exception e) {
      log.error("Failed to send TD attribution not written notification", e);
    }
  }

  void notifyAttributionFailed(
      TulevaFund fund, LocalDate periodStart, LocalDate periodEnd, String reason) {
    try {
      notificationService.sendMessage(
          """
          ⚠️ TD ATTRIBUTION FAILED: fund=%s, period=%s to %s
            The attribution errored (%s), so nothing was written for
            this period. Any attribution already stored for it is left as it was. Rerun it once
            the cause is fixed; the stack trace is in the logs."""
              .formatted(fund.getCode(), periodStart, periodEnd, reason),
          INVESTMENT);
    } catch (Exception e) {
      log.error("Failed to send TD attribution failed notification", e);
    }
  }

  private static String toBps(BigDecimal value) {
    return value
        .multiply(new BigDecimal("10000"))
        .setScale(2, RoundingMode.HALF_UP)
        .toPlainString();
  }

  void notifyBackfillSummary(int daysBack, List<TrackingDifferenceResult> results) {
    sendBackfillSummary(BACKFILL_COMPLETE_HEADER.formatted(daysBack), daysBack, results);
  }

  void notifyIncompleteBackfillSummary(int daysBack, List<TrackingDifferenceResult> results) {
    sendBackfillSummary(BACKFILL_INCOMPLETE_HEADER.formatted(daysBack), daysBack, results);
  }

  private void sendBackfillSummary(
      String header, int daysBack, List<TrackingDifferenceResult> results) {
    try {
      if (results.isEmpty()) {
        notificationService.sendMessage(
            """
            ⚠️ TD BACKFILL produced no results: daysBack=%d
              Nothing was rewritten, so every stored event still carries the definition it was
              written with. The reason per fund is in the logs."""
                .formatted(daysBack),
            INVESTMENT);
        return;
      }

      var message = new StringBuilder(header);
      results.stream()
          .collect(
              Collectors.groupingBy(
                  r -> "%s %s".formatted(r.fund().getCode(), r.checkType()),
                  TreeMap::new,
                  Collectors.toList()))
          .forEach(
              (fundAndCheckType, group) ->
                  message.append(formatBackfillGroup(fundAndCheckType, group)));
      notificationService.sendMessage(message.toString(), INVESTMENT);
    } catch (Exception e) {
      log.error("Failed to send tracking difference backfill summary", e);
    }
  }

  private static String formatBackfillGroup(
      String fundAndCheckType, List<TrackingDifferenceResult> group) {
    var dates = group.stream().map(TrackingDifferenceResult::checkDate).sorted().toList();
    var breaches = group.stream().filter(TrackingDifferenceResult::hasAnyBreach).count();
    var worst =
        group.stream()
            .map(TrackingDifferenceResult::trackingDifference)
            .max(Comparator.comparing(BigDecimal::abs))
            .orElse(BigDecimal.ZERO);
    return "\n  %s: %d check dates %s to %s, %d breaches, largest TD %s%%"
        .formatted(
            fundAndCheckType,
            dates.size(),
            dates.getFirst(),
            dates.getLast(),
            breaches,
            formatPercent(worst));
  }

  private static List<TrackingDifferenceResult> alertableResults(
      List<TrackingDifferenceResult> results) {
    return results.stream().filter(r -> r.checkType() != BENCHMARK).toList();
  }

  void notifyGapFillSummary(GapFillRun run) {
    try {
      var alertableResults = alertableResults(run.results());
      if (alertableResults.isEmpty()) {
        return;
      }
      notificationService.sendMessage(
          GapFillSummaryFormatter.format(alertableResults, run.recheckedStaleDates())
              + notificationsOwedOnRewrittenCleanDays(alertableResults),
          INVESTMENT);
    } catch (Exception e) {
      log.error("Failed to send tracking difference gap fill summary", e);
    }
  }

  private String notificationsOwedOnRewrittenCleanDays(
      List<TrackingDifferenceResult> alertableResults) {
    var owed =
        alertableResults.stream()
            .filter(result -> !result.hasAnyBreach() && result.streakBefore() != null)
            .map(result -> EscalationRule.on(result.checkDate(), calculator).judge(result))
            .filter(Verdict::escalation)
            .toList();
    return owed.isEmpty() ? "" : "\n\n" + formatAlerts(owed);
  }

  void notify(List<TrackingDifferenceResult> results) {
    try {
      var alertableResults = alertableResults(results);
      if (alertableResults.isEmpty()) {
        notificationService.sendMessage(
            """
            ⚠️ TD RUN produced no tracking difference results to alert on
              Nothing actionable was checked: every fund was skipped, or only the suppressed ACWI
              benchmark ran. The reason per fund is in the logs; the daily series is unchanged.""",
            INVESTMENT);
        return;
      }
      var verdicts =
          alertableResults.stream()
              .map(result -> EscalationRule.on(result.checkDate(), calculator).judge(result))
              .toList();
      if (verdicts.stream().noneMatch(Verdict::alerts)) {
        notificationService.sendMessage(formatAllWithinLimits(alertableResults), INVESTMENT);
        return;
      }
      notificationService.sendMessage(formatAlerts(verdicts), INVESTMENT);
    } catch (Exception e) {
      log.error("Failed to send tracking difference notification", e);
    }
  }

  private static String formatAllWithinLimits(List<TrackingDifferenceResult> alertableResults) {
    var byFund =
        alertableResults.stream()
            .collect(
                Collectors.groupingBy(r -> r.fund().getCode(), TreeMap::new, Collectors.toList()));
    var message = new StringBuilder();
    byFund.forEach(
        (fundCode, fundResults) -> {
          if (message.length() > 0) {
            message.append("\n");
          }
          message.append("✅ %s TD check completed: within limits".formatted(fundCode));
          fundResults.stream()
              .sorted(Comparator.comparing(r -> r.checkType().name()))
              .forEach(
                  r ->
                      message
                          .append(formatWithinLimits(r))
                          .append(formatCountWarnings(r))
                          .append(formatBenchmarkGap(r)));
        });
    return message.toString();
  }

  private String formatAlerts(List<Verdict> verdicts) {
    var message = new StringBuilder();
    verdicts.forEach(verdict -> message.append(formatAlert(verdict)));
    if (verdicts.stream().anyMatch(Verdict::breached)) {
      message.insert(0, "🛑 TD BREACH DETECTED\n");
    }
    if (verdicts.stream().anyMatch(Verdict::escalation)) {
      message.insert(0, "🛑 TD ESCALATION — CONSECUTIVE BREACH DAYS\n");
    }
    if (verdicts.stream().anyMatch(Verdict::decidedOnFallback)) {
      message.append(
          "\n⚠️ The escalation parameters are not configured, so this was decided on built-in"
              + " fallback constants rather than the configured rule. Seed"
              + " ESCALATION_THRESHOLD_DAYS and ESCALATION_NET_TD_THRESHOLD.");
    }
    return message.toString();
  }

  private String formatAlert(Verdict verdict) {
    var result = verdict.result();
    if (verdict.breached()) {
      return new BreachMessageFormatter(result, verdict.escalation(), redemptionCycle(result))
              .format()
          + formatCountWarnings(result)
          + formatBenchmarkGap(result);
    }
    if (verdict.escalation()) {
      return EndedStreakNotice.format(result, verdict.notificationWorkingDay())
          + formatCountWarnings(result)
          + formatBenchmarkGap(result);
    }
    return formatCountWarnings(result);
  }

  private @Nullable RedemptionCycleHint redemptionCycle(TrackingDifferenceResult result) {
    if (result.checkType() != MODEL_PORTFOLIO) {
      return null;
    }
    return redemptionCycleLookup.resolve(result.fund(), result.checkDate());
  }

  private static String returnLabel(TrackingDifferenceResult result) {
    return result.checkType() == BENCHMARK_MODEL ? "holdings" : "fund";
  }

  private static String formatWithinLimits(TrackingDifferenceResult result) {
    var sb = new StringBuilder();
    sb.append(
        "\n  %s TD=%s%% (%s=%s%%, benchmark=%s%%)"
            .formatted(
                result.checkType(),
                formatPercent(result.trackingDifference()),
                returnLabel(result),
                formatPercent(result.fundReturn()),
                formatPercent(result.benchmarkReturn())));
    if (result.checkType() == MODEL_PORTFOLIO) {
      var navResidual = result.navResidual();
      if (navResidual != null) {
        sb.append(", navResidual %s%%".formatted(formatPercent(navResidual)));
      } else {
        sb.append(", navResidual not evaluated (begin-of-day holdings unavailable)");
      }
    }
    return sb.toString();
  }

  private static String formatBenchmarkGap(TrackingDifferenceResult result) {
    var gapIsins = result.benchmarkGapIsins();
    if (gapIsins == null || gapIsins.isEmpty()) {
      return "";
    }
    var weight = result.benchmarkGapWeight();
    return "\n  \u26a0\ufe0f %s of the sleeve has no benchmark proxy with data behind it, so this check did not"
            .formatted(weight == null ? "Part" : formatPercent(weight) + "%")
        + " measure it: %s. Every holding should have one - fix the instrument reference data."
            .formatted(gapIsins);
  }

  private static String formatCountWarnings(TrackingDifferenceResult result) {
    var sb = new StringBuilder();
    var subject = "%s %s".formatted(result.fund().getCode(), result.checkType());
    if (result.escalationCountUnavailable()) {
      sb.append(
          "\n  ⚠️ %s: the breach streak could not be counted, so this check cannot say whether the"
                  .formatted(subject)
              + " breach has persisted. Escalation is not being evaluated for it.");
    }
    if (result.escalationCountTruncated()) {
      sb.append(
          "\n  ⚠️ %s: the streak fills the whole lookback window, so it has run for at least %s days"
                  .formatted(subject, result.consecutiveBreachDays())
              + " and the net TD above is a lower bound. Widen ESCALATION_LOOKBACK_DAYS to measure"
              + " it.");
    }
    return sb.toString();
  }

  private static String formatPercent(BigDecimal value) {
    var percent = value.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
    return (percent.signum() > 0 ? "+" : "") + percent.toPlainString();
  }
}
