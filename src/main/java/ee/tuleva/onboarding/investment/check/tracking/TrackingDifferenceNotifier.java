package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.check.tracking.TrackingCheckType.BENCHMARK;
import static ee.tuleva.onboarding.investment.check.tracking.TrackingCheckType.BENCHMARK_MODEL;
import static ee.tuleva.onboarding.investment.check.tracking.TrackingCheckType.MODEL_PORTFOLIO;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class TrackingDifferenceNotifier {

  private static final int ESCALATION_THRESHOLD_FALLBACK = 3;
  private static final BigDecimal ESCALATION_NET_TD_THRESHOLD_FALLBACK = new BigDecimal("0.002");
  private static final BigDecimal HUNDRED = new BigDecimal("100");

  private final OperationsNotificationService notificationService;
  private final TrackingDifferenceCalculator calculator;

  void notify(List<TrackingDifferenceResult> results) {
    try {
      var alertableResults = results.stream().filter(r -> r.checkType() != BENCHMARK).toList();
      var hasAnyBreaches = alertableResults.stream().anyMatch(TrackingDifferenceResult::breach);

      if (!hasAnyBreaches) {
        var fundCodes =
            alertableResults.stream()
                .map(r -> r.fund().getCode())
                .distinct()
                .sorted()
                .collect(Collectors.joining(", "));
        notificationService.sendMessage(
            "%s TD check completed: within limits".formatted(fundCodes), INVESTMENT);
        return;
      }

      var message = new StringBuilder("TD BREACH DETECTED\n");
      var hasEscalation = false;

      for (var result : alertableResults) {
        if (!result.breach()) {
          continue;
        }

        var escalation = isEscalation(result);
        if (escalation) {
          hasEscalation = true;
        }

        message.append(formatBreach(result, escalation));
      }

      if (hasEscalation) {
        message.insert(0, "TD ESCALATION — CONSECUTIVE BREACH DAYS\n");
      }

      notificationService.sendMessage(message.toString(), INVESTMENT);
    } catch (Exception e) {
      log.error("Failed to send tracking difference notification", e);
    }
  }

  private String formatBreach(TrackingDifferenceResult result, boolean escalation) {
    var sb = new StringBuilder();
    sb.append(
        "\n[%s] %s %s: TD=%s%% (fund=%s%%, benchmark=%s%%)"
            .formatted(
                result.fund(),
                result.checkType(),
                result.checkDate(),
                formatPercent(result.trackingDifference()),
                formatPercent(result.fundReturn()),
                formatPercent(result.benchmarkReturn())));

    if (result.checkType() == MODEL_PORTFOLIO) {
      sb.append("\n  Action: check NAV calculation — weights, prices, cash, fees");
    } else if (result.checkType() == BENCHMARK_MODEL) {
      sb.append("\n  Action: review instrument prices and benchmark data for pricing errors");
    }

    if (!result.securityAttributions().isEmpty()) {
      var sorted =
          result.securityAttributions().stream()
              .sorted(
                  Comparator.comparing(
                      (SecurityAttribution a) -> a.contribution().abs(), Comparator.reverseOrder()))
              .toList();

      if (result.checkType() == BENCHMARK_MODEL) {
        for (var attr : sorted) {
          sb.append(
              "\n  %s: instrument %s%%, benchmark %s%%, diff %s%%"
                  .formatted(
                      attr.isin(),
                      formatPercent(attr.securityReturn()),
                      formatPercent(attr.benchmarkReturn()),
                      formatPercent(attr.contribution())));
        }
      } else {
        for (var attr : sorted) {
          sb.append(
              "\n  %s: weight %s%%, return %s%%, impact %s%%"
                  .formatted(
                      attr.isin(),
                      formatPercent(attr.weightDifference()),
                      formatPercent(attr.securityReturn()),
                      formatPercent(attr.contribution())));
        }

        if (result.cashDrag().signum() != 0) {
          sb.append("\n  Cash drag: %s%%".formatted(formatPercent(result.cashDrag())));
        }
        if (result.feeDrag().signum() != 0) {
          sb.append("\n  Fee drag: %s%%".formatted(formatPercent(result.feeDrag())));
        }
        if (result.residual().signum() != 0) {
          sb.append("\n  Residual: %s%%".formatted(formatPercent(result.residual())));
        }
      }
    }

    if (escalation) {
      sb.append(
          "\n  [%d consecutive days, compounded TD=%s%%]"
              .formatted(result.consecutiveBreachDays(), formatPercent(result.consecutiveNetTd())));
      if (result.compoundedFundReturn() != null && result.compoundedBenchmarkReturn() != null) {
        sb.append(
            "\n  Compounded: fund=%s%%, benchmark=%s%%"
                .formatted(
                    formatPercent(result.compoundedFundReturn()),
                    formatPercent(result.compoundedBenchmarkReturn())));
      }

      if (result.escalationAttributions() != null && !result.escalationAttributions().isEmpty()) {
        sb.append("\n  Multi-day attribution (summed daily):");
        result.escalationAttributions().entrySet().stream()
            .sorted(
                java.util.Comparator.comparing(
                    (java.util.Map.Entry<String, BigDecimal> e) -> e.getValue().abs(),
                    java.util.Comparator.reverseOrder()))
            .forEach(
                e ->
                    sb.append("\n    %s: %s%%".formatted(e.getKey(), formatPercent(e.getValue()))));

        if (result.escalationCashDrag() != null && result.escalationCashDrag().signum() != 0) {
          sb.append("\n    Cash drag: %s%%".formatted(formatPercent(result.escalationCashDrag())));
        }
        if (result.escalationFeeDrag() != null && result.escalationFeeDrag().signum() != 0) {
          sb.append("\n    Fee drag: %s%%".formatted(formatPercent(result.escalationFeeDrag())));
        }
        if (result.escalationResidual() != null && result.escalationResidual().signum() != 0) {
          sb.append("\n    Residual: %s%%".formatted(formatPercent(result.escalationResidual())));
        }
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
    } catch (Exception e) {
      threshold = ESCALATION_THRESHOLD_FALLBACK;
      netTdThreshold = ESCALATION_NET_TD_THRESHOLD_FALLBACK;
    }
    return result.consecutiveBreachDays() >= threshold
        && result.consecutiveNetTd() != null
        && result.consecutiveNetTd().abs().compareTo(netTdThreshold) >= 0;
  }

  private String formatPercent(BigDecimal value) {
    var percent = value.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
    return (percent.signum() > 0 ? "+" : "") + percent.toPlainString();
  }
}
