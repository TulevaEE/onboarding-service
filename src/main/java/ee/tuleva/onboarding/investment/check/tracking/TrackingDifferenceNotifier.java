package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.TrackingCheckType.BENCHMARK;
import static ee.tuleva.onboarding.investment.TrackingCheckType.BENCHMARK_MODEL;
import static ee.tuleva.onboarding.investment.TrackingCheckType.MODEL_PORTFOLIO;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;

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
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class TrackingDifferenceNotifier {

  // Sisekord nr 4 p 11.7: escalate once the breach "püsib enam kui kolm (3) tööpäeva", so the
  // fourth consecutive breach day is the first that escalates.
  private static final int ESCALATION_THRESHOLD_FALLBACK = 4;
  private static final BigDecimal ESCALATION_NET_TD_THRESHOLD_FALLBACK = new BigDecimal("0.001");
  private static final BigDecimal HUNDRED = new BigDecimal("100");

  private final OperationsNotificationService notificationService;
  private final TrackingDifferenceCalculator calculator;

  void notifyCheckCouldNotRun(TulevaFund fund, LocalDate navDate) {
    try {
      notificationService.sendMessage(
          "⚠️ TD CHECK DID NOT RUN: fund=%s, date=%s — missing NAV, prices, or model data; NAV report published WITHOUT tracking-difference validation"
              .formatted(fund.getCode(), navDate),
          INVESTMENT);
    } catch (Exception e) {
      log.error("Failed to send tracking difference 'check did not run' notification", e);
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

  void notify(List<TrackingDifferenceResult> results) {
    try {
      var alertableResults = results.stream().filter(r -> r.checkType() != BENCHMARK).toList();
      // The ACWI benchmark is suppressed from alerts, so a run holding only benchmark results
      // checked nothing anyone acts on - the same empty run as no results at all.
      if (alertableResults.isEmpty()) {
        notificationService.sendMessage(
            """
            ⚠️ TD RUN produced no tracking difference results to alert on
              Nothing actionable was checked: every fund was skipped, or only the suppressed ACWI
              benchmark ran. The reason per fund is in the logs; the daily series is unchanged.""",
            INVESTMENT);
        return;
      }
      var hasAnyBreaches =
          alertableResults.stream().anyMatch(TrackingDifferenceResult::hasAnyBreach);

      if (!hasAnyBreaches) {
        var byFund =
            alertableResults.stream()
                .collect(
                    Collectors.groupingBy(
                        r -> r.fund().getCode(), TreeMap::new, Collectors.toList()));
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
        notificationService.sendMessage(message.toString(), INVESTMENT);
        return;
      }

      var message = new StringBuilder("🛑 TD BREACH DETECTED\n");
      var hasEscalation = false;

      var decidedOnFallbackConfig = false;

      for (var result : alertableResults) {
        if (!result.hasAnyBreach()) {
          message.append(formatCountWarnings(result));
          continue;
        }

        var rule = escalationRule(result.checkDate());
        var escalation = isEscalation(result, rule);
        if (escalation) {
          hasEscalation = true;
          decidedOnFallbackConfig = decidedOnFallbackConfig || rule.fallback();
        }

        message
            .append(new BreachMessageFormatter(result, escalation).format())
            .append(formatCountWarnings(result))
            .append(formatBenchmarkGap(result));
      }

      if (hasEscalation) {
        message.insert(0, "🛑 TD ESCALATION — CONSECUTIVE BREACH DAYS\n");
      }
      if (decidedOnFallbackConfig) {
        message.append(
            "\n⚠️ The escalation parameters are not configured, so this was decided on built-in"
                + " fallback constants rather than the configured rule. Seed"
                + " ESCALATION_THRESHOLD_DAYS and ESCALATION_NET_TD_THRESHOLD.");
      }

      notificationService.sendMessage(message.toString(), INVESTMENT);
    } catch (Exception e) {
      log.error("Failed to send tracking difference notification", e);
    }
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

  private record EscalationRule(int thresholdDays, BigDecimal netTdThreshold, boolean fallback) {}

  private EscalationRule escalationRule(LocalDate checkDate) {
    try {
      return new EscalationRule(
          calculator.escalationThresholdDays(checkDate),
          calculator.escalationNetTdThreshold(checkDate),
          false);
    } catch (Exception e) {
      log.error("Escalation parameters unavailable, using fallback: {}", e.getMessage());
      return new EscalationRule(
          ESCALATION_THRESHOLD_FALLBACK, ESCALATION_NET_TD_THRESHOLD_FALLBACK, true);
    }
  }

  private boolean isEscalation(TrackingDifferenceResult result, EscalationRule rule) {
    return result.consecutiveBreachDays() >= rule.thresholdDays()
        && ((result.consecutiveNetTd() != null
                && result.consecutiveNetTd().abs().compareTo(rule.netTdThreshold()) >= 0)
            || result.escalationNavResidualBreach());
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
    if (result.escalationCountUnavailable()) {
      sb.append(
          "\n  ⚠️ The breach streak could not be counted, so this check cannot say whether the"
              + " breach has persisted. Escalation is not being evaluated for it.");
    }
    if (result.escalationCountTruncated()) {
      sb.append(
          "\n  ⚠️ The streak fills the whole lookback window, so it has run for at least %s days"
                  .formatted(result.consecutiveBreachDays())
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
