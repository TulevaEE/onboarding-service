package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;
import static java.time.ZoneOffset.UTC;
import static java.time.format.DateTimeFormatter.ofPattern;
import static java.util.Locale.US;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.check.tracking.BenchmarkModelTrackingDifference;
import ee.tuleva.onboarding.investment.check.tracking.TrackingDifferenceQueryService;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.savings.fund.nav.NavCalculationResult.SecurityDetail;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class NavNotifier {

  private static final DateTimeFormatter CALCULATED_AT_FORMAT =
      ofPattern("yyyy-MM-dd HH:mm:ss").withZone(UTC);

  private final OperationsNotificationService notificationService;
  private final PublicHolidays publicHolidays;
  private final FundNavQueryService fundNavQueryService;
  private final TrackingDifferenceQueryService trackingDifferenceQueryService;

  void notify(NavCalculationResult result) {
    try {
      var message =
          formatMessage(result, dayChangeRatio(result), benchmarkModelTrackingDifference(result));
      log.info(message);
      notificationService.sendMessage(message, SAVINGS);
    } catch (Exception e) {
      log.error(
          "Failed to send NAV notification: fund={}, date={}",
          result.fund(),
          result.calculationDate(),
          e);
    }
  }

  String formatMessage(
      NavCalculationResult result,
      Optional<BigDecimal> dayChangeRatio,
      Optional<BenchmarkModelTrackingDifference> trackingDifference) {
    var message = new StringBuilder();

    message.append("NAV Calculation — %s\n\n".formatted(result.fund().getCode()));

    message.append("Dates:\n");
    message.append("  NAV Date: %s\n".formatted(result.positionReportDate()));
    message.append("  Price Date: %s\n".formatted(result.priceDate()));
    message.append(
        "  Calculated At: %s\n".formatted(CALCULATED_AT_FORMAT.format(result.calculatedAt())));

    message.append("\nAssets:\n");
    appendAmount(message, "Securities", result.securitiesValue());
    appendAmount(message, "Cash", result.cashPosition());
    appendAmount(message, "Receivables", result.receivables());
    appendAmount(message, "Pending Subscriptions", result.pendingSubscriptions());
    appendAmount(message, "BlackRock Adj", result.blackrockAdjustment());

    message.append("\nLiabilities:\n");
    appendAmount(message, "Payables", result.payables());
    appendAmount(message, "Pending Redemptions", result.pendingRedemptions());
    appendAmount(message, "Mgmt Fee Accrual", result.managementFeeAccrual());
    appendAmount(message, "Depot Fee Accrual", result.depotFeeAccrual());

    message.append("\nSummary:\n");
    appendAmount(message, "Total Assets", result.totalAssets());
    appendAmount(message, "Total Liabilities", result.totalLiabilities());
    appendAmount(message, "AUM", result.aum());
    message.append(
        "  Units Outstanding: %s\n"
            .formatted(result.unitsOutstanding().stripTrailingZeros().toPlainString()));
    message.append(
        "  *NAV/Unit: %s%s*\n"
            .formatted(
                result.navPerUnit().toPlainString(),
                dayChangeRatio.map(ratio -> " (" + formatSignedPercent(ratio) + ")").orElse("")));
    appendTrackingDifference(message, trackingDifference);

    appendSecuritiesDetail(message, result);

    return message.toString();
  }

  private Optional<BigDecimal> dayChangeRatio(NavCalculationResult result) {
    try {
      var previousNavDate = publicHolidays.previousWorkingDay(result.positionReportDate());
      return fundNavQueryService
          .findNavPerUnit(result.fund().getCode(), previousNavDate)
          .filter(previous -> previous.signum() != 0)
          .map(
              previous ->
                  result.navPerUnit().subtract(previous).divide(previous, 6, RoundingMode.HALF_UP));
    } catch (Exception e) {
      log.warn(
          "Failed to compute NAV day change: fund={}, date={}",
          result.fund(),
          result.positionReportDate(),
          e);
      return Optional.empty();
    }
  }

  private Optional<BenchmarkModelTrackingDifference> benchmarkModelTrackingDifference(
      NavCalculationResult result) {
    try {
      return trackingDifferenceQueryService.findLatestBenchmarkModel(
          result.fund(), result.positionReportDate());
    } catch (Exception e) {
      log.warn(
          "Failed to fetch tracking difference: fund={}, date={}",
          result.fund(),
          result.positionReportDate(),
          e);
      return Optional.empty();
    }
  }

  private void appendTrackingDifference(
      StringBuilder message, Optional<BenchmarkModelTrackingDifference> trackingDifference) {
    var value =
        trackingDifference
            .map(
                difference ->
                    "%s %s"
                        .formatted(
                            formatSignedPercent(difference.trackingDifference()),
                            difference.breachesLimit() ? "🔴" : "✅"))
            .orElse("n/a");
    message.append("  Tracking Difference (BENCHMARK_MODEL): %s\n".formatted(value));
  }

  private String formatSignedPercent(BigDecimal ratio) {
    var percent = ratio.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    var sign = percent.signum() >= 0 ? "+" : "";
    return sign + percent.toPlainString() + "%";
  }

  private void appendAmount(StringBuilder message, String label, BigDecimal value) {
    message.append(String.format(US, "  %s: %,.2f EUR\n", label, value));
  }

  private void appendSecuritiesDetail(StringBuilder message, NavCalculationResult result) {
    if (result.securitiesDetail() == null || result.securitiesDetail().isEmpty()) {
      return;
    }

    message.append("\nSecurities Detail:\n");
    result
        .securitiesDetail()
        .forEach(detail -> appendSecurityLine(message, detail, result.priceDate()));
  }

  private void appendSecurityLine(
      StringBuilder message, SecurityDetail detail, LocalDate targetPriceDate) {
    String icon = stalenessIcon(detail.priceDate(), targetPriceDate);
    if (detail.price() != null) {
      message.append(
          String.format(
              US,
              "  %s %s (%s): %s × %s = %,.2f EUR [%s]\n",
              icon,
              detail.isin(),
              detail.ticker(),
              detail.units().stripTrailingZeros().toPlainString(),
              detail.price().stripTrailingZeros().toPlainString(),
              detail.marketValue(),
              detail.priceDate()));
    } else {
      message.append(
          "  ❌ %s (%s): %s units (no price)\n"
              .formatted(
                  detail.isin(),
                  detail.ticker(),
                  detail.units().stripTrailingZeros().toPlainString()));
    }
  }

  private String stalenessIcon(LocalDate priceDate, LocalDate targetPriceDate) {
    if (priceDate == null) {
      return "❌";
    }
    long daysBehind = publicHolidays.countWorkingDaysBehind(priceDate, targetPriceDate);
    if (daysBehind == 0) return "✅";
    if (daysBehind == 1) return "⚠️";
    return "❌";
  }
}
