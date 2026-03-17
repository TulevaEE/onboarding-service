package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.fund.TulevaFund.TUV100;
import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.savings.fund.nav.NavCalculationResult.SecurityDetail;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class NavReportMapper {

  private static final BigDecimal ONE = new BigDecimal("1.00");

  private final FundPositionRepository fundPositionRepository;

  List<NavReportRow> map(NavCalculationResult result) {
    var rows = new ArrayList<NavReportRow>();
    var fund = result.fund();
    var navDate = result.positionReportDate();
    var fundCode = fund.getCode();
    var accountId = fund.getIsin();

    result.securitiesDetail().forEach(detail -> rows.add(securityRow(navDate, fund, detail)));

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
      rows.add(receivablesRow(navDate, fundCode, accountId, "Other receivables", BigDecimal.ZERO));
    }

    if (fund == TUK00) {
      rows.add(liabilityRow(navDate, fundCode, accountId, "Liabilities Other", BigDecimal.ZERO));
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

    if (fund == TKF100 || fund == TUV100) {
      rows.add(
          liabilityFeeRow(navDate, fundCode, accountId, "Custody fee", result.depotFeeAccrual()));
    }

    rows.add(unitsRow(navDate, fundCode, result));
    rows.add(navRow(navDate, fundCode, result));

    return rows;
  }

  private NavReportRow securityRow(LocalDate navDate, TulevaFund fund, SecurityDetail detail) {
    var displayName =
        fundPositionRepository
            .findByNavDateAndFundAndAccountTypeAndAccountId(navDate, fund, SECURITY, detail.isin())
            .map(FundPosition::getAccountName)
            .orElse(detail.isin());

    return NavReportRow.builder()
        .navDate(navDate)
        .fundCode(fund.getCode())
        .accountType("SECURITY")
        .accountName(displayName)
        .accountId(detail.isin())
        .quantity(detail.units())
        .marketPrice(detail.price())
        .marketValue(detail.marketValue())
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
        .quantity(cashPosition)
        .marketPrice(ONE)
        .marketValue(cashPosition)
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
        .quantity(amount)
        .marketPrice(ONE)
        .marketValue(amount)
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
        .quantity(amount.negate())
        .marketPrice(ONE)
        .marketValue(amount.negate())
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
        .quantity(amount.negate())
        .marketPrice(ONE)
        .marketValue(amount.negate())
        .build();
  }

  private NavReportRow unitsRow(LocalDate navDate, String fundCode, NavCalculationResult result) {
    return NavReportRow.builder()
        .navDate(navDate)
        .fundCode(fundCode)
        .accountType("UNITS")
        .accountName("Total outstanding units:")
        .quantity(result.unitsOutstanding())
        .marketPrice(result.navPerUnit())
        .marketValue(result.aum())
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
        .marketValue(result.navPerUnit())
        .build();
  }
}
