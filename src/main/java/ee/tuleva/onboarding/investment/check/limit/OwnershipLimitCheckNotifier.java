package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.OK;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.INFO;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING;
import static java.math.RoundingMode.HALF_UP;
import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.investment.check.limit.OwnershipCheckRun.NotChecked;
import ee.tuleva.onboarding.investment.check.limit.OwnershipCheckRun.Result;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.notification.OperationsNotificationService.Severity;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class OwnershipLimitCheckNotifier {

  private static final BigDecimal MILLION = new BigDecimal("1000000");
  private static final int PERCENT_DECIMAL_PLACES = 2;
  private static final String EUR = "EUR";
  private static final String LINE_INDENT = "  ";
  private static final String NOT_VERIFIED_ICON = "⏸";
  private static final String LEFT_OUT_ICON = "ℹ️";

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
      case OK -> run.hasStaleFundSizes() ? WARNING : INFO;
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
                    run.results().stream().flatMap(OwnershipLimitCheckNotifier::resultLines),
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
                  : "✅ Ownership limit check OK: month=%s".formatted(run.month());
    };
  }

  private static Stream<String> resultLines(Result result) {
    return Stream.of(
            Stream.of(summaryLine(result)),
            result.holdings().stream()
                .filter(holding -> holding.severity() != OK)
                .map(holding -> breachLine(result, holding)),
            result.unverified().stream().map(holding -> unverifiedLine(result, holding)),
            result.leftOut().stream().map(holding -> leftOutLine(result, holding)),
            result.staleSizes().stream().map(stale -> staleLine(result, stale)))
        .flatMap(lines -> lines);
  }

  private static String summaryLine(Result result) {
    return result
        .largest()
        .map(
            largest ->
                "%s %s %s: %d of %d holdings checked, largest %s%% of %s (%s) in a %s fund — %s"
                    .formatted(
                        icon(result.worstSeverity()),
                        result.fund().getCode(),
                        result.checkDate(),
                        result.holdings().size(),
                        result.holdings().size() + result.unverified().size(),
                        percent(largest.actualPercent()),
                        largest.name(),
                        largest.isin(),
                        millions(largest.underlyingFundSize(), EUR),
                        limits(largest)))
        .orElseGet(() -> nothingMeasuredLine(result));
  }

  private static String nothingMeasuredLine(Result result) {
    return result.unverified().isEmpty()
        ? "%s %s %s: no securities found to check"
            .formatted(NOT_VERIFIED_ICON, result.fund().getCode(), result.checkDate())
        : "%s %s %s: none of %d holdings could be checked"
            .formatted(
                NOT_VERIFIED_ICON,
                result.fund().getCode(),
                result.checkDate(),
                result.unverified().size());
  }

  private static String breachLine(Result result, OwnershipBreach holding) {
    return "%s [%s] %s %s %s (%s): %s%% — holding %s of a %s fund%s, %s"
        .formatted(
            icon(holding.severity()),
            holding.severity(),
            result.fund().getCode(),
            result.checkDate(),
            holding.name(),
            holding.isin(),
            percent(holding.actualPercent()),
            millions(holding.holdingValue(), EUR),
            millions(holding.underlyingFundSize(), EUR),
            asReported(holding),
            limits(holding));
  }

  private static String unverifiedLine(Result result, UnverifiedHolding holding) {
    return "%s %s %s %s (%s): not verified — %s"
        .formatted(
            NOT_VERIFIED_ICON,
            result.fund().getCode(),
            result.checkDate(),
            holding.name(),
            Objects.requireNonNullElse(holding.isin(), "no ISIN"),
            holding.reason());
  }

  private static String staleLine(Result result, StaleFundSize stale) {
    return "⚠️ %s %s %s (%s): EODHD fund size %s unchanged since %s — probably stale, so the share may be wrong"
        .formatted(
            result.fund().getCode(),
            result.checkDate(),
            stale.name(),
            stale.isin(),
            millions(stale.reportedFundSize(), stale.reportedCurrency()),
            stale.unchangedSince());
  }

  private static String leftOutLine(Result result, LeftOutHolding holding) {
    return "%s %s %s %s (%s): left out by design — %s"
        .formatted(
            LEFT_OUT_ICON,
            result.fund().getCode(),
            result.checkDate(),
            holding.name(),
            holding.isin(),
            holding.reason());
  }

  private static String notCheckedLine(NotChecked notChecked) {
    return "%s Not checked: %s — %s"
        .formatted(NOT_VERIFIED_ICON, notChecked.fund().getCode(), notChecked.reason());
  }

  private static String asReported(OwnershipBreach holding) {
    var updated =
        holding.reportedUpdatedAt() == null
            ? "update date unknown"
            : "updated " + holding.reportedUpdatedAt();
    return " (EODHD: %s, %s)"
        .formatted(millions(holding.reportedFundSize(), holding.reportedCurrency()), updated);
  }

  private static String limits(OwnershipBreach holding) {
    return "soft %s%%, hard %s%%"
        .formatted(
            holding.softLimitPercent().stripTrailingZeros().toPlainString(),
            holding.hardLimitPercent().stripTrailingZeros().toPlainString());
  }

  private static String percent(BigDecimal value) {
    return value.setScale(PERCENT_DECIMAL_PLACES, HALF_UP).toPlainString();
  }

  private static String millions(BigDecimal amount, String currency) {
    return "%sM %s".formatted(amount.divide(MILLION, 2, HALF_UP).toPlainString(), currency);
  }

  private static String icon(BreachSeverity severity) {
    return switch (severity) {
      case HARD -> "🛑";
      case SOFT -> "⚠️";
      case OK -> "✅";
    };
  }
}
