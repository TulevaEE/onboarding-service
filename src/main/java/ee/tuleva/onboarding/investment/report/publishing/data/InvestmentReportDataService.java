package ee.tuleva.onboarding.investment.report.publishing.data;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.report.publishing.FundReportMapping;
import ee.tuleva.onboarding.investment.report.publishing.pdf.InvestmentReportContext;
import ee.tuleva.onboarding.investment.report.publishing.pdf.InvestmentReportContext.SecuritySection;
import ee.tuleva.onboarding.investment.report.publishing.pdf.InvestmentReportRow;
import ee.tuleva.onboarding.investment.transaction.PortfolioCostBasisService;
import ee.tuleva.onboarding.investment.transaction.PortfolioCostBasisSnapshot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvestmentReportDataService {

  private static final DateTimeFormatter ESTONIAN_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

  private final NavReportViewRepository navReportRepository;
  private final PortfolioCostBasisService costBasisService;
  private final InvestmentReportRowsBuilder rowsBuilder;

  public Map<String, LocalDate> findNavDatesForAllFunds(YearMonth month) {
    var startDate = month.atDay(1);
    var endDate = month.atEndOfMonth();
    var result = new LinkedHashMap<String, LocalDate>();
    for (var mapping : FundReportMapping.all()) {
      var navDate =
          navReportRepository.findLatestPublishedNavDate(
              mapping.fund().getCode(), startDate, endDate);
      if (navDate != null) {
        result.put(mapping.fund().getCode(), navDate);
      }
    }
    return result;
  }

  public List<String> validateQuantities(TulevaFund fund, YearMonth month) {
    var startDate = month.atDay(1);
    var endDate = month.atEndOfMonth();
    var navDate =
        navReportRepository.findLatestPublishedNavDate(fund.getCode(), startDate, endDate);
    if (navDate == null) {
      return List.of();
    }

    var navRows =
        navReportRepository.findPublishedByNavDateAndFundCode(navDate, fund.getCode()).stream()
            .filter(r -> "SECURITY".equals(r.getAccountType()))
            .toList();

    var costBasisMap =
        costBasisService.snapshotForFundAndDate(fund, navDate).stream()
            .collect(Collectors.toMap(PortfolioCostBasisSnapshot::instrumentIsin, s -> s));

    var errors = new ArrayList<String>();
    for (var nav : navRows) {
      var isin = nav.getAccountId();
      var snapshot = costBasisMap.get(isin);
      if (snapshot == null) {
        errors.add(
            "%s %s: no cost basis data (NAV quantity=%s)"
                .formatted(fund.getCode(), isin, nav.getQuantity()));
        continue;
      }
      if (nav.getQuantity() != null && nav.getQuantity().compareTo(snapshot.quantity()) != 0) {
        errors.add(
            "%s %s: quantity mismatch — NAV=%s, costBasis=%s"
                .formatted(fund.getCode(), isin, nav.getQuantity(), snapshot.quantity()));
      }
    }
    return errors;
  }

  public InvestmentReportContext getReportData(TulevaFund fund, YearMonth month) {
    var mapping = FundReportMapping.forFund(fund);
    var startDate = month.atDay(1);
    var endDate = month.atEndOfMonth();

    var navDate =
        navReportRepository.findLatestPublishedNavDate(fund.getCode(), startDate, endDate);
    if (navDate == null) {
      throw new IllegalStateException(
          "No published NAV data for fund=%s, month=%s".formatted(fund.getCode(), month));
    }

    var currentRows =
        navReportRepository.findPublishedByNavDateAndFundCode(navDate, fund.getCode());
    if (currentRows.isEmpty()) {
      throw new IllegalStateException(
          "No NAV report rows for fund=%s, navDate=%s".formatted(fund.getCode(), navDate));
    }

    var fundNav = findFundNav(currentRows);

    var prevMonth = month.minusMonths(1);
    var prevPctMap = buildPreviousMonthPercentages(fund, prevMonth);

    var securities =
        currentRows.stream().filter(r -> "SECURITY".equals(r.getAccountType())).toList();
    var cashRows =
        currentRows.stream()
            .filter(r -> "CASH".equals(r.getAccountType()))
            .filter(r -> r.getMarketValue() != null && r.getMarketValue().signum() != 0)
            .toList();
    var receivablesTotal =
        currentRows.stream()
            .filter(r -> "RECEIVABLES".equals(r.getAccountType()))
            .filter(r -> !"Total receivables of unsettled transactions".equals(r.getAccountName()))
            .filter(r -> r.getMarketValue() != null && r.getMarketValue().signum() > 0)
            .map(NavReportView::getMarketValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    var costBasisMap =
        costBasisService.snapshotForFundAndDate(fund, navDate).stream()
            .collect(Collectors.toMap(PortfolioCostBasisSnapshot::instrumentIsin, s -> s));

    var rows = rowsBuilder.buildRows(securities, costBasisMap, cashRows, receivablesTotal, fundNav);
    var securityRows = rows.securityRows();
    var secTotalCost = InvestmentReportRowsBuilder.securitiesTotalCostIfComplete(securityRows);
    var secTotalMarketValue =
        securityRows.stream()
            .map(InvestmentReportRow::marketValueTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    var secTotalNavPercent =
        securityRows.stream()
            .map(InvestmentReportRow::navSharePercent)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    var secTotalChange =
        prevPctMap.securitiesTotal() != null
            ? secTotalNavPercent.subtract(prevPctMap.securitiesTotal())
            : null;

    var section =
        new SecuritySection(
            mapping.sectionHeading(),
            securityRows,
            secTotalCost,
            secTotalMarketValue,
            secTotalNavPercent,
            secTotalChange);

    var cashReportRows = rows.cashRows();
    var cashTotalMv =
        cashReportRows.stream()
            .map(InvestmentReportRow::marketValueTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    var cashTotalNavPct =
        cashReportRows.stream()
            .map(InvestmentReportRow::navSharePercent)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    var cashPrevTotal = sumNullable(prevPctMap.cashTotal(), prevPctMap.receivablesTotal());
    var cashTotalChange = cashPrevTotal != null ? cashTotalNavPct.subtract(cashPrevTotal) : null;

    var totalAssetsMv = secTotalMarketValue.add(cashTotalMv);
    var totalAssetsCost = secTotalCost != null ? secTotalCost.add(cashTotalMv) : null;
    var totalAssetsNavPct =
        fundNav.signum() != 0
            ? totalAssetsMv.divide(fundNav, 6, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

    return new InvestmentReportContext(
        fund.getDisplayName(),
        navDate.format(ESTONIAN_DATE),
        List.of(section),
        secTotalCost,
        secTotalMarketValue,
        secTotalNavPercent,
        secTotalChange,
        cashReportRows,
        cashTotalMv,
        cashTotalNavPct,
        cashTotalChange,
        totalAssetsMv,
        totalAssetsCost,
        totalAssetsNavPct,
        fundNav);
  }

  private BigDecimal findFundNav(List<NavReportView> rows) {
    var fundNav =
        rows.stream()
            .filter(r -> "UNITS".equals(r.getAccountType()))
            .map(NavReportView::getMarketValue)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No UNITS row in NAV report"));
    if (fundNav == null || fundNav.signum() <= 0) {
      throw new IllegalStateException("Fund NAV must be positive: " + fundNav);
    }
    return fundNav;
  }

  private PrevMonthPercentages buildPreviousMonthPercentages(TulevaFund fund, YearMonth prevMonth) {
    var startDate = prevMonth.atDay(1);
    var endDate = prevMonth.atEndOfMonth();
    var prevNavDate =
        navReportRepository.findLatestPublishedNavDate(fund.getCode(), startDate, endDate);
    if (prevNavDate == null) {
      return PrevMonthPercentages.empty();
    }

    var prevRows =
        navReportRepository.findPublishedByNavDateAndFundCode(prevNavDate, fund.getCode());
    if (prevRows.isEmpty()) {
      return PrevMonthPercentages.empty();
    }

    var prevNav =
        prevRows.stream()
            .filter(r -> "UNITS".equals(r.getAccountType()))
            .map(NavReportView::getMarketValue)
            .findFirst()
            .orElse(null);
    if (prevNav == null || prevNav.signum() == 0) {
      return PrevMonthPercentages.empty();
    }

    var secTotal = BigDecimal.ZERO;
    BigDecimal cashTotal = null;
    BigDecimal recTotal = null;

    for (var r : prevRows) {
      if ("SECURITY".equals(r.getAccountType()) && r.getMarketValue() != null) {
        secTotal = secTotal.add(r.getMarketValue().divide(prevNav, 6, RoundingMode.HALF_UP));
      } else if ("CASH".equals(r.getAccountType())
          && r.getMarketValue() != null
          && r.getMarketValue().signum() != 0) {
        var pct = r.getMarketValue().divide(prevNav, 6, RoundingMode.HALF_UP);
        cashTotal = cashTotal != null ? cashTotal.add(pct) : pct;
      } else if ("RECEIVABLES".equals(r.getAccountType())
          && !"Total receivables of unsettled transactions".equals(r.getAccountName())
          && r.getMarketValue() != null
          && r.getMarketValue().signum() > 0) {
        var pct = r.getMarketValue().divide(prevNav, 6, RoundingMode.HALF_UP);
        recTotal = recTotal != null ? recTotal.add(pct) : pct;
      }
    }

    return new PrevMonthPercentages(secTotal, cashTotal, recTotal);
  }

  private static @Nullable BigDecimal sumNullable(@Nullable BigDecimal a, @Nullable BigDecimal b) {
    if (a == null && b == null) return null;
    if (a == null) return b;
    if (b == null) return a;
    return a.add(b);
  }

  private record PrevMonthPercentages(
      @Nullable BigDecimal securitiesTotal,
      @Nullable BigDecimal cashTotal,
      @Nullable BigDecimal receivablesTotal) {
    static PrevMonthPercentages empty() {
      return new PrevMonthPercentages(null, null, null);
    }
  }
}
