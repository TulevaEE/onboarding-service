package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.savings.fund.nav.NavReportAccountNames.*;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.instrument.InstrumentReference;
import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import ee.tuleva.onboarding.savings.fund.nav.NavCalculationResult.SecurityDetail;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class NavReportMapper {

  private static final BigDecimal ONE = new BigDecimal("1.00");

  private final InstrumentReferenceService instrumentReferenceService;

  List<NavReportRow> map(NavCalculationResult result) {
    var rows = new ArrayList<NavReportRow>();
    var fund = result.fund();
    var navDate = result.positionReportDate();
    var fundCode = fund.getCode();
    var accountId = fund.getIsin();

    sortedSecurities(fund, result.securitiesDetail()).stream()
        .filter(detail -> !isFullyDeinvested(detail))
        .forEach(detail -> rows.add(securityRow(navDate, fundCode, detail)));

    rows.add(cashRow(navDate, fundCode, accountId, result.cashPosition()));
    rows.add(receivablesRow(navDate, fundCode, accountId, TRADE_RECEIVABLES, result.receivables()));
    rows.add(liabilityRow(navDate, fundCode, accountId, TRADE_PAYABLES, result.payables()));
    rows.add(
        receivablesRow(
            navDate, fundCode, accountId, PENDING_SUBSCRIPTIONS, result.pendingSubscriptions()));

    if (!fund.isSavingsFund()) {
      rows.add(
          receivablesRow(
              navDate,
              fundCode,
              accountId,
              BLACKROCK_RECEIVABLE,
              result.blackrockAdjustment().max(ZERO)));
    }

    if (!fund.isSavingsFund()) {
      rows.add(
          liabilityRow(
              navDate,
              fundCode,
              accountId,
              BLACKROCK_LIABILITY,
              result.blackrockAdjustment().min(ZERO).negate()));
    }

    rows.add(
        liabilityRow(
            navDate, fundCode, accountId, PENDING_REDEMPTIONS, result.pendingRedemptions()));
    rows.add(
        liabilityFeeRow(
            navDate, fundCode, accountId, MANAGEMENT_FEE, result.managementFeeAccrual()));
    rows.add(liabilityFeeRow(navDate, fundCode, accountId, CUSTODY_FEE, result.depotFeeAccrual()));

    rows.add(unitsRow(navDate, fundCode, result));
    rows.add(navRow(navDate, fundCode, result));

    return rows;
  }

  private boolean isFullyDeinvested(SecurityDetail detail) {
    return detail.units().signum() == 0 && detail.marketValue().signum() == 0;
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
        instrumentReferenceService
            .findByIsin(detail.isin())
            .map(InstrumentReference::getDisplayName)
            .orElse(detail.isin());

    return NavReportRow.builder()
        .navDate(navDate)
        .fundCode(fundCode)
        .accountType("SECURITY")
        .accountName(displayName)
        .accountId(detail.isin())
        .quantity(detail.units().setScale(3, HALF_UP))
        .marketPrice(detail.price())
        .marketValue(detail.marketValue().setScale(2, HALF_UP))
        .build();
  }

  private NavReportRow cashRow(
      LocalDate navDate, String fundCode, String accountId, BigDecimal cashPosition) {
    return NavReportRow.builder()
        .navDate(navDate)
        .fundCode(fundCode)
        .accountType("CASH")
        .accountName(CASH)
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
        .accountName(UNITS_OUTSTANDING)
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
        .accountName(NET_ASSET_VALUE)
        .quantity(ONE)
        .marketPrice(result.navPerUnit())
        .marketValue(result.navPerUnit().setScale(2, HALF_UP))
        .build();
  }
}
