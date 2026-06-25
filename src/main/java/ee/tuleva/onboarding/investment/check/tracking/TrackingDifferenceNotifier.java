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

  // The tracking-difference check produced no results for this fund/date (missing NAV pair or model
  // data), so the NAV report is being published WITHOUT a tracking-difference validation. The gate
  // does not block on this (fail-open), but it must be surfaced explicitly rather than reported as
  // "within limits".
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
      // A navResidualBreach blocks the NAV report even when the fund-vs-model TD is within limits
      // (a NAV error whose effect happens to offset the model deviation), so it must alert too —
      // otherwise the desk sees a block with an all-clear notification.
      var hasAnyBreaches =
          alertableResults.stream().anyMatch(r -> r.breach() || r.navResidualBreach());

      if (!hasAnyBreaches) {
        var fundCodes =
            alertableResults.stream()
                .map(r -> r.fund().getCode())
                .distinct()
                .sorted()
                .collect(Collectors.joining(", "));
        var message = "%s TD check completed: within limits".formatted(fundCodes);
        // Disclose when the NAV-correctness residual could not be evaluated (begin-of-day holdings
        // unavailable) so "within limits" is not read as "NAV residual validated clean".
        var notEvaluated =
            alertableResults.stream()
                .filter(r -> r.checkType() == MODEL_PORTFOLIO && r.impliedFundReturn() == null)
                .map(r -> r.fund().getCode())
                .distinct()
                .sorted()
                .collect(Collectors.joining(", "));
        if (!notEvaluated.isEmpty()) {
          message +=
              " (NAV residual NOT evaluated for %s — begin-of-day holdings unavailable)"
                  .formatted(notEvaluated);
        }
        notificationService.sendMessage(message, INVESTMENT);
        return;
      }

      var message = new StringBuilder("TD BREACH DETECTED\n");
      var hasEscalation = false;

      for (var result : alertableResults) {
        if (!result.breach() && !result.navResidualBreach()) {
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

    // NAV-correctness signal: the gate blocks on navResidual, not on the fund-vs-model TD above.
    // Surfacing it tells the desk whether this breach actually blocked the NAV report or was an
    // expected trade-day deviation (navResidual ~0) that passed. impliedFundReturn is the
    // "evaluated" sentinel — null means the begin-of-day snapshot was unavailable and the
    // navResidual gate was skipped, which must NOT read as "validated clean".
    if (result.checkType() == MODEL_PORTFOLIO) {
      if (result.impliedFundReturn() != null) {
        sb.append(
            "\n  NAV residual: %s%% (%s)"
                .formatted(
                    formatPercent(result.navResidual()),
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
        && result.consecutiveNetTd() != null
        && result.consecutiveNetTd().abs().compareTo(netTdThreshold) >= 0;
  }

  private String formatPercent(BigDecimal value) {
    var percent = value.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
    return (percent.signum() > 0 ? "+" : "") + percent.toPlainString();
  }
}
