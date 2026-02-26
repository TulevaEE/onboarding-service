package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;
import static java.util.Locale.US;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.savings.fund.nav.NavCalculationResult.SecurityDetail;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class NavNotifier {

  private final OperationsNotificationService notificationService;

  void notify(NavCalculationResult result) {
    try {
      var message = formatMessage(result);
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

  String formatMessage(NavCalculationResult result) {
    var message = new StringBuilder();

    message.append("NAV Calculation — %s\n\n".formatted(result.fund().getCode()));

    message.append("Dates:\n");
    message.append("  Calculation Date: %s\n".formatted(result.calculationDate()));
    message.append("  NAV Date: %s\n".formatted(result.positionReportDate()));
    message.append("  Price Date: %s\n".formatted(result.priceDate()));
    message.append("  Calculated At: %s\n".formatted(result.calculatedAt()));

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
        "  NAV/Unit: %s\n".formatted(result.navPerUnit().stripTrailingZeros().toPlainString()));

    appendSecuritiesDetail(message, result);

    return message.toString();
  }

  private void appendAmount(StringBuilder message, String label, BigDecimal value) {
    message.append(String.format(US, "  %s: %,.2f EUR\n", label, value));
  }

  private void appendSecuritiesDetail(StringBuilder message, NavCalculationResult result) {
    if (result.securitiesDetail() == null || result.securitiesDetail().isEmpty()) {
      return;
    }

    message.append("\nSecurities Detail:\n");
    result.securitiesDetail().forEach(detail -> appendSecurityLine(message, detail));
  }

  private void appendSecurityLine(StringBuilder message, SecurityDetail detail) {
    if (detail.price() != null) {
      message.append(
          String.format(
              US,
              "  %s (%s): %s × %s = %,.2f EUR\n",
              detail.isin(),
              detail.ticker(),
              detail.units().stripTrailingZeros().toPlainString(),
              detail.price().stripTrailingZeros().toPlainString(),
              detail.marketValue()));
    } else {
      message.append(
          "  %s (%s): %s units (no price)\n"
              .formatted(
                  detail.isin(),
                  detail.ticker(),
                  detail.units().stripTrailingZeros().toPlainString()));
    }
  }
}
