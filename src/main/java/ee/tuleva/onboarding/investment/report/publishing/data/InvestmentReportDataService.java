package ee.tuleva.onboarding.investment.report.publishing.data;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.report.publishing.FundReportMapping;
import ee.tuleva.onboarding.investment.report.publishing.pdf.InvestmentReportContext;
import ee.tuleva.onboarding.investment.report.publishing.pdf.InvestmentReportContext.SecuritySection;
import ee.tuleva.onboarding.investment.report.publishing.pdf.InvestmentReportRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvestmentReportDataService {

  private static final DateTimeFormatter ESTONIAN_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

  private final NavReportViewRepository navReportRepository;
  private final InstrumentReferenceRepository instrumentReferenceRepository;

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

    var instrumentMap = loadInstrumentMap(securities);

    var securityRows = buildSecurityRows(securities, instrumentMap, fundNav);
    var secTotalCost =
        securityRows.stream()
            .map(InvestmentReportRow::avgCostTotal)
            .filter(v -> v != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
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
            secTotalCost.signum() != 0 ? secTotalCost : null,
            secTotalMarketValue,
            secTotalNavPercent,
            secTotalChange);

    var cashReportRows = buildCashRows(cashRows, receivablesTotal, fundNav);
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
    var totalAssetsCost = secTotalCost.signum() != 0 ? secTotalCost.add(cashTotalMv) : null;
    var totalAssetsNavPct =
        fundNav.signum() != 0
            ? totalAssetsMv.divide(fundNav, 6, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

    return new InvestmentReportContext(
        fund.getDisplayName(),
        navDate.format(ESTONIAN_DATE),
        List.of(section),
        secTotalCost.signum() != 0 ? secTotalCost : null,
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
    return rows.stream()
        .filter(r -> "UNITS".equals(r.getAccountType()))
        .map(NavReportView::getMarketValue)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No UNITS row in NAV report"));
  }

  private Map<String, InstrumentReference> loadInstrumentMap(List<NavReportView> securities) {
    var isins = securities.stream().map(NavReportView::getAccountId).distinct().toList();
    return instrumentReferenceRepository.findByIsinIn(isins).stream()
        .collect(Collectors.toMap(InstrumentReference::getIsin, Function.identity()));
  }

  private List<InvestmentReportRow> buildSecurityRows(
      List<NavReportView> securities,
      Map<String, InstrumentReference> instrumentMap,
      BigDecimal fundNav) {
    return securities.stream()
        .map(
            sec -> {
              var ref = instrumentMap.get(sec.getAccountId());
              var displayName = ref != null ? ref.getDisplayName() : sec.getAccountName();
              var manager = ref != null ? ref.getFundManager() : null;
              var country = ref != null ? ref.getCountry() : null;
              var navPct =
                  fundNav.signum() != 0
                      ? sec.getMarketValue().divide(fundNav, 6, RoundingMode.HALF_UP)
                      : BigDecimal.ZERO;

              return new InvestmentReportRow(
                  displayName,
                  manager,
                  sec.getAccountId(),
                  country,
                  "EUR",
                  null,
                  null,
                  sec.getMarketPrice(),
                  sec.getMarketValue(),
                  navPct,
                  null);
            })
        .toList();
  }

  private List<InvestmentReportRow> buildCashRows(
      List<NavReportView> cashRows, BigDecimal receivablesTotal, BigDecimal fundNav) {
    var rows = new ArrayList<InvestmentReportRow>();

    if (receivablesTotal.signum() > 0) {
      var pct =
          fundNav.signum() != 0
              ? receivablesTotal.divide(fundNav, 6, RoundingMode.HALF_UP)
              : BigDecimal.ZERO;
      rows.add(
          new InvestmentReportRow(
              "Muud nõuded",
              null,
              null,
              "EE",
              "EUR",
              null,
              null,
              null,
              receivablesTotal,
              pct,
              null));
    }

    for (var cash : cashRows) {
      var cashInfo = formatCashAccount(cash.getAccountName());
      var pct =
          fundNav.signum() != 0
              ? cash.getMarketValue().divide(fundNav, 6, RoundingMode.HALF_UP)
              : BigDecimal.ZERO;
      rows.add(
          new InvestmentReportRow(
              cashInfo.name(),
              cashInfo.institution(),
              null,
              "EE",
              "EUR",
              null,
              null,
              null,
              cash.getMarketValue(),
              pct,
              null));
    }
    return rows;
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
      if ("SECURITY".equals(r.getAccountType())) {
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

  private static BigDecimal sumNullable(BigDecimal a, BigDecimal b) {
    if (a == null && b == null) return null;
    if (a == null) return b;
    if (b == null) return a;
    return a.add(b);
  }

  static CashAccountInfo formatCashAccount(String accountName) {
    if (accountName == null) return new CashAccountInfo("Arvelduskonto", accountName);
    var lower = accountName.toLowerCase();
    if (lower.contains("seb")) return new CashAccountInfo("Arvelduskonto", "AS SEB Pank");
    if (lower.contains("swedbank")) return new CashAccountInfo("Arvelduskonto", "Swedbank AS");
    if (lower.contains("lhv")) return new CashAccountInfo("Arvelduskonto", "AS LHV Pank");
    if (lower.contains("luminor")) return new CashAccountInfo("Arvelduskonto", "Luminor Bank AS");
    return new CashAccountInfo("Arvelduskonto", accountName);
  }

  record CashAccountInfo(String name, String institution) {}

  private record PrevMonthPercentages(
      BigDecimal securitiesTotal, BigDecimal cashTotal, BigDecimal receivablesTotal) {
    static PrevMonthPercentages empty() {
      return new PrevMonthPercentages(null, null, null);
    }
  }
}
