package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.check.tracking.TrackingCheckType.BENCHMARK;
import static ee.tuleva.onboarding.investment.check.tracking.TrackingCheckType.BENCHMARK_MODEL;
import static ee.tuleva.onboarding.investment.check.tracking.TrackingCheckType.MODEL_PORTFOLIO;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
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

  private static final int ESCALATION_THRESHOLD_FALLBACK = 3;
  private static final BigDecimal ESCALATION_NET_TD_THRESHOLD_FALLBACK = new BigDecimal("0.005");
  private static final BigDecimal HUNDRED = new BigDecimal("100");

  private final OperationsNotificationService notificationService;
  private final TrackingDifferenceCalculator calculator;

  void notifyCheckCouldNotRun(TulevaFund fund, LocalDate navDate) {
    try {
      notificationService.sendMessage(
          "TD CHECK DID NOT RUN: fund=%s, date=%s — missing NAV, prices, or model data; NAV report published WITHOUT tracking-difference validation"
              .formatted(fund.getCode(), navDate),
          INVESTMENT);
    } catch (Exception e) {
      log.error("Failed to send tracking difference 'check did not run' notification", e);
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
          message.append("%s TD check completed: within limits".formatted(fundCodes));
        }
        byFund.forEach(
            (fundCode, fundResults) -> {
              if (message.length() > 0) {
                message.append("\n");
              }
              message.append("%s TD check completed: within limits".formatted(fundCode));
              fundResults.stream()
                  .sorted(Comparator.comparing(r -> r.checkType().name()))
                  .forEach(r -> message.append(formatWithinLimits(r)));
            });
        notificationService.sendMessage(message.toString(), INVESTMENT);
        return;
      }

      var message = new StringBuilder("TD BREACH DETECTED\n");
      var hasEscalation = false;

      for (var result : alertableResults) {
        if (!result.hasAnyBreach()) {
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

  private String returnLabel(TrackingDifferenceResult result) {
    return result.checkType() == BENCHMARK_MODEL ? "holdings" : "fund";
  }

  private String formatWithinLimits(TrackingDifferenceResult result) {
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

  private String formatBreach(TrackingDifferenceResult result, boolean escalation) {
    var sb = new StringBuilder();
    sb.append(
        "\n[%s] %s %s: TD=%s%% (%s=%s%%, benchmark=%s%%)"
            .formatted(
                result.fund(),
                result.checkType(),
                result.checkDate(),
                formatPercent(result.trackingDifference()),
                returnLabel(result),
                formatPercent(result.fundReturn()),
                formatPercent(result.benchmarkReturn())));

    if (result.checkType() == MODEL_PORTFOLIO) {
      sb.append("\n  Action: check NAV calculation — weights, prices, cash, fees");
    } else if (result.checkType() == BENCHMARK_MODEL) {
      sb.append(
          "\n  Holdings vs MSCI World/EM index. Regional/ESG spread is expected;"
              + " check an outsized contribution for a stale price.");
    }

    if (result.checkType() == MODEL_PORTFOLIO) {
      var navResidual = result.navResidual();
      if (navResidual != null) {
        sb.append(
            "\n  NAV residual: %s%% (%s)"
                .formatted(
                    formatPercent(navResidual),
                    result.navResidualBreach()
                        ? "BLOCKS NAV — investigate pricing / NAV calc"
                        : "non-blocking — fund-vs-model TD explained by trade timing"));
      } else {
        sb.append(
            "\n  NAV residual: not evaluated — begin-of-day holdings unavailable (gate skipped)");
      }
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
              "\n  %s: instrument %s%%, index %s%%, contributes %s%% to TD"
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
        sb.append("\n  Multi-day attribution (arithmetic sum of daily contributions):");
        result.escalationAttributions().entrySet().stream()
            .sorted(
                java.util.Comparator.comparing(
                    (java.util.Map.Entry<String, BigDecimal> e) -> e.getValue().abs(),
                    java.util.Comparator.reverseOrder()))
            .forEach(
                e ->
                    sb.append("\n    %s: %s%%".formatted(e.getKey(), formatPercent(e.getValue()))));
      }

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

  private String formatPercent(BigDecimal value) {
    var percent = value.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
    return (percent.signum() > 0 ? "+" : "") + percent.toPlainString();
  }
}
