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
      var hasAnyBreaches =
          alertableResults.stream().anyMatch(TrackingDifferenceResult::hasAnyBreach);

      if (!hasAnyBreaches) {
        var byFund =
            alertableResults.stream()
                .collect(
                    Collectors.groupingBy(
                        r -> r.fund().getCode(), TreeMap::new, Collectors.toList()));
        var message = new StringBuilder();
        if (byFund.isEmpty()) {
          var fundCodes =
              results.stream()
                  .map(r -> r.fund().getCode())
                  .distinct()
                  .sorted()
                  .collect(Collectors.joining(", "));
          message.append("✅ %s TD check completed: within limits".formatted(fundCodes));
        }
        byFund.forEach(
            (fundCode, fundResults) -> {
              if (message.length() > 0) {
                message.append("\n");
              }
              message.append("✅ %s TD check completed: within limits".formatted(fundCode));
              fundResults.stream()
                  .sorted(Comparator.comparing(r -> r.checkType().name()))
                  .forEach(r -> message.append(formatWithinLimits(r)));
            });
        notificationService.sendMessage(message.toString(), INVESTMENT);
        return;
      }

      var message = new StringBuilder("🛑 TD BREACH DETECTED\n");
      var hasEscalation = false;

      for (var result : alertableResults) {
        if (!result.hasAnyBreach()) {
          continue;
        }

        var escalation = isEscalation(result);
        if (escalation) {
          hasEscalation = true;
        }

        message.append(new BreachMessageFormatter(result, escalation).format());
      }

      if (hasEscalation) {
        message.insert(0, "🛑 TD ESCALATION — CONSECUTIVE BREACH DAYS\n");
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

  private boolean isEscalation(TrackingDifferenceResult result) {
    int threshold;
    BigDecimal netTdThreshold;
    try {
      threshold = calculator.escalationThresholdDays(result.checkDate());
      netTdThreshold = calculator.escalationNetTdThreshold(result.checkDate());
    } catch (IllegalStateException e) {
      log.warn("Escalation parameters not configured, using fallback: {}", e.getMessage());
      threshold = ESCALATION_THRESHOLD_FALLBACK;
      netTdThreshold = ESCALATION_NET_TD_THRESHOLD_FALLBACK;
    } catch (Exception e) {
      log.warn("Escalation parameter lookup failed, using fallback: {}", e.getMessage());
      threshold = ESCALATION_THRESHOLD_FALLBACK;
      netTdThreshold = ESCALATION_NET_TD_THRESHOLD_FALLBACK;
    }
    return result.consecutiveBreachDays() >= threshold
        && ((result.consecutiveNetTd() != null
                && result.consecutiveNetTd().abs().compareTo(netTdThreshold) >= 0)
            || result.escalationNavResidualBreach());
  }

  private static String formatPercent(BigDecimal value) {
    var percent = value.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
    return (percent.signum() > 0 ? "+" : "") + percent.toPlainString();
  }
}
