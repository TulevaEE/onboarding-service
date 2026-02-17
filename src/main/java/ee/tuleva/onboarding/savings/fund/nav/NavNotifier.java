package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;
import static java.util.Locale.US;

import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundTicker;
import ee.tuleva.onboarding.investment.calculation.PositionPriceResolver;
import ee.tuleva.onboarding.investment.calculation.ResolvedPrice;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class NavNotifier {

  private final OperationsNotificationService notificationService;
  private final NavLedgerRepository navLedgerRepository;
  private final PositionPriceResolver positionPriceResolver;

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

    message.append(
        "NAV Calculation — %s (%s)\n\n"
            .formatted(result.fund().getCode(), result.calculationDate()));

    message.append("Assets:\n");
    appendAmount(message, "Securities", result.securitiesValue());
    appendAmount(message, "Cash", result.cashPosition());
    appendAmount(message, "Receivables", result.receivables());
    appendAmount(message, "Pending Subscriptions", result.pendingSubscriptions());

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
    var unitBalances = navLedgerRepository.getSecuritiesUnitBalances();
    if (unitBalances.isEmpty()) {
      return;
    }

    message.append("\nSecurities Detail:\n");
    new TreeMap<>(unitBalances)
        .forEach((isin, units) -> appendSecurityLine(message, isin, units, result.priceDate()));
  }

  private void appendSecurityLine(
      StringBuilder message, String isin, BigDecimal units, LocalDate priceDate) {
    var ticker = FundTicker.findByIsin(isin).map(FundTicker::getEodhdTicker).orElse("UNKNOWN");

    var price =
        positionPriceResolver.resolve(isin, priceDate).map(ResolvedPrice::usedPrice).orElse(null);

    if (price != null) {
      var marketValue = units.multiply(price);
      message.append(
          String.format(
              US,
              "  %s (%s): %s × %s = %,.2f EUR\n",
              isin,
              ticker,
              units.stripTrailingZeros().toPlainString(),
              price.stripTrailingZeros().toPlainString(),
              marketValue));
    } else {
      message.append(
          "  %s (%s): %s units (no price)\n"
              .formatted(isin, ticker, units.stripTrailingZeros().toPlainString()));
    }
  }
}
