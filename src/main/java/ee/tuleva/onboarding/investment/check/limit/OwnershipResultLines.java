package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.OK;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.investment.check.limit.OwnershipCheckRun.Result;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.stream.Stream;

final class OwnershipResultLines {

  static final String NOT_VERIFIED_ICON = "⏸";
  private static final String LEFT_OUT_ICON = "ℹ️";
  private static final BigDecimal MILLION = new BigDecimal("1000000");
  private static final int PERCENT_DECIMAL_PLACES = 2;
  private static final String EUR = "EUR";

  private OwnershipResultLines() {}

  static Stream<String> of(Result result) {
    return Stream.of(
            Stream.of(summaryLine(result)),
            result.holdings().stream()
                .filter(holding -> holding.severity() != OK)
                .map(holding -> breachLine(result, holding)),
            result.unverified().stream().map(holding -> unverifiedLine(result, holding)),
            result.leftOut().stream().map(holding -> leftOutLine(result, holding)),
            result.staleSizes().stream().map(stale -> staleLine(result, stale)),
            Stream.ofNullable(result.sizesNotComparedWithTheMonthBefore())
                .map(reason -> notComparedLine(result, reason)))
        .flatMap(lines -> lines);
  }

  private static String summaryLine(Result result) {
    return result
        .largest()
        .map(
            largest ->
                "%s %s %s: %s, largest %s%% of %s (%s) in a %s fund — %s"
                    .formatted(
                        icon(result.worstSeverity()),
                        result.fund().getCode(),
                        result.checkDate(),
                        holdingsChecked(result),
                        percent(largest.actualPercent()),
                        largest.name(),
                        largest.isin(),
                        millions(largest.underlyingFundSize(), EUR),
                        limits(largest)))
        .orElseGet(() -> nothingMeasuredLine(result));
  }

  private static String holdingsChecked(Result result) {
    var checked =
        "%d of %d holdings checked"
            .formatted(
                result.holdings().size(),
                result.holdings().size() + result.unverified().size() + result.leftOut().size());
    return result.leftOut().isEmpty() ? checked : checked + ", " + leftOutByDesign(result);
  }

  private static String leftOutByDesign(Result result) {
    return "%d left out by design".formatted(result.leftOut().size());
  }

  private static String nothingMeasuredLine(Result result) {
    if (result.everyHoldingWasLeftOutByDesign()) {
      return "%s %s %s: no holding to size, %s"
          .formatted(
              icon(OK), result.fund().getCode(), result.checkDate(), leftOutByDesign(result));
    }
    if (result.unverified().isEmpty()) {
      return "%s %s %s: no securities found to check"
          .formatted(NOT_VERIFIED_ICON, result.fund().getCode(), result.checkDate());
    }
    var noneChecked =
        "%s %s %s: none of %d holdings could be checked"
            .formatted(
                NOT_VERIFIED_ICON,
                result.fund().getCode(),
                result.checkDate(),
                result.unverified().size() + result.leftOut().size());
    return result.leftOut().isEmpty() ? noneChecked : noneChecked + ", " + leftOutByDesign(result);
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

  private static String notComparedLine(Result result, String reason) {
    return "⚠️ %s %s: fund sizes not compared with the month before, so a stale one would go unflagged — %s"
        .formatted(result.fund().getCode(), result.checkDate(), reason);
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
