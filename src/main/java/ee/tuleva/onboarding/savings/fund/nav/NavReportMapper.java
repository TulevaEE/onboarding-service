package ee.tuleva.onboarding.savings.fund.nav;

import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundTicker;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.savings.fund.nav.NavCalculationResult.SecurityDetail;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class NavReportMapper {

  private static final BigDecimal ONE = new BigDecimal("1.00");

  List<NavReportRow> map(NavCalculationResult result) {
    var rows = new ArrayList<NavReportRow>();
    var fund = result.fund();
    var navDate = result.positionReportDate();
    var fundCode = fund.getCode();
    var accountId = fund.getIsin();

    sortedSecurities(fund, result.securitiesDetail())
        .forEach(detail -> rows.add(securityRow(navDate, fundCode, detail)));

    rows.add(cashRow(navDate, fundCode, accountId, result.cashPosition()));
    rows.add(
        receivablesRow(
            navDate,
            fundCode,
            accountId,
            "Total receivables of unsettled transactions",
            result.receivables()));
    rows.add(
        liabilityRow(
            navDate,
            fundCode,
            accountId,
            "Total payables of unsettled transactions",
            result.payables()));
    rows.add(
        receivablesRow(
            navDate,
            fundCode,
            accountId,
            "Receivables of outstanding units",
            result.pendingSubscriptions()));

    if (!fund.isSavingsFund()) {
      rows.add(
          receivablesRow(
              navDate,
              fundCode,
              accountId,
              "Other receivables",
              result.blackrockAdjustment().max(ZERO)));
    }

    if (!fund.isSavingsFund()) {
      rows.add(
          liabilityRow(
              navDate,
              fundCode,
              accountId,
              "Liabilities Other",
              result.blackrockAdjustment().min(ZERO).negate()));
    }

    rows.add(
        liabilityRow(
            navDate,
            fundCode,
            accountId,
            "Payables of redeemed units",
            result.pendingRedemptions()));
    rows.add(
        liabilityFeeRow(
            navDate, fundCode, accountId, "Management fee", result.managementFeeAccrual()));
    rows.add(
        liabilityFeeRow(navDate, fundCode, accountId, "Custody fee", result.depotFeeAccrual()));

    rows.add(unitsRow(navDate, fundCode, result));
    rows.add(navRow(navDate, fundCode, result));

    return rows;
  }

  private List<SecurityDetail> sortedSecurities(TulevaFund fund, List<SecurityDetail> securities) {
    var order = fund.getModelPortfolioOrder();
    return securities.stream()
        .sorted(
            Comparator.comparingInt(
                (SecurityDetail d) -> {
                  int idx = order.indexOf(d.isin());
                  return idx >= 0 ? idx : Integer.MAX_VALUE;
                }))
        .toList();
  }

  private NavReportRow securityRow(LocalDate navDate, String fundCode, SecurityDetail detail) {
    var displayName =
        FundTicker.findByIsin(detail.isin()).map(FundTicker::getDisplayName).orElse(detail.isin());

    return NavReportRow.builder()
        .navDate(navDate)
        .fundCode(fundCode)
        .accountType("SECURITY")
        .accountName(displayName)
        .accountId(detail.isin())
        .quantity(detail.units().setScale(3, HALF_UP))
        .marketPrice(detail.price().setScale(4, HALF_UP))
        .marketValue(detail.marketValue().setScale(2, HALF_UP))
        .build();
  }

  private NavReportRow cashRow(
      LocalDate navDate, String fundCode, String accountId, BigDecimal cashPosition) {
    return NavReportRow.builder()
        .navDate(navDate)
        .fundCode(fundCode)
        .accountType("CASH")
        .accountName("Cash account in SEB Pank")
        .accountId(accountId)
        .quantity(cashPosition.setScale(2, HALF_UP))
        .marketPrice(ONE)
        .marketValue(cashPosition.setScale(2, HALF_UP))
        .build();
  }

  private NavReportRow receivablesRow(
      LocalDate navDate, String fundCode, String accountId, String accountName, BigDecimal amount) {
    return NavReportRow.builder()
        .navDate(navDate)
        .fundCode(fundCode)
        .accountType("RECEIVABLES")
        .accountName(accountName)
        .accountId(accountId)
        .quantity(amount.setScale(2, HALF_UP))
        .marketPrice(ONE)
        .marketValue(amount.setScale(2, HALF_UP))
        .build();
  }

  private NavReportRow liabilityRow(
      LocalDate navDate, String fundCode, String accountId, String accountName, BigDecimal amount) {
    return NavReportRow.builder()
        .navDate(navDate)
        .fundCode(fundCode)
        .accountType("LIABILITY")
        .accountName(accountName)
        .accountId(accountId)
        .quantity(amount.negate().setScale(2, HALF_UP))
        .marketPrice(ONE)
        .marketValue(amount.negate().setScale(2, HALF_UP))
        .build();
  }

  private NavReportRow liabilityFeeRow(
      LocalDate navDate, String fundCode, String accountId, String accountName, BigDecimal amount) {
    return NavReportRow.builder()
        .navDate(navDate)
        .fundCode(fundCode)
        .accountType("LIABILITY_FEE")
        .accountName(accountName)
        .accountId(accountId)
        .quantity(amount.negate().setScale(2, HALF_UP))
        .marketPrice(ONE)
        .marketValue(amount.negate().setScale(2, HALF_UP))
        .build();
  }

  private NavReportRow unitsRow(LocalDate navDate, String fundCode, NavCalculationResult result) {
    return NavReportRow.builder()
        .navDate(navDate)
        .fundCode(fundCode)
        .accountType("UNITS")
        .accountName("Total outstanding units:")
        .quantity(result.unitsOutstanding().setScale(3, HALF_UP))
        .marketPrice(result.navPerUnit())
        .marketValue(result.aum().setScale(2, HALF_UP))
        .build();
  }

  private NavReportRow navRow(LocalDate navDate, String fundCode, NavCalculationResult result) {
    return NavReportRow.builder()
        .navDate(navDate)
        .fundCode(fundCode)
        .accountType("NAV")
        .accountName("Net Asset Value")
        .quantity(ONE)
        .marketPrice(result.navPerUnit())
        .marketValue(result.navPerUnit().setScale(2, HALF_UP))
        .build();
  }
}
