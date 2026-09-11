package ee.tuleva.onboarding.investment.report.publishing.data;

import ee.tuleva.onboarding.instrument.InstrumentReference;
import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import ee.tuleva.onboarding.investment.report.publishing.pdf.InvestmentReportRow;
import ee.tuleva.onboarding.investment.transaction.PortfolioCostBasisSnapshot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class InvestmentReportRowsBuilder {

  private final InstrumentReferenceService instrumentReferenceService;

  RowsResult buildRows(
      List<NavReportView> securities,
      Map<String, PortfolioCostBasisSnapshot> costBasisMap,
      List<NavReportView> cashRows,
      BigDecimal receivablesTotal,
      BigDecimal fundNav) {
    var securityRows = buildSecurityRows(securities, costBasisMap, fundNav);
    var cashReportRows = buildCashRows(cashRows, receivablesTotal, fundNav);
    return new RowsResult(securityRows, cashReportRows);
  }

  List<InvestmentReportRow> buildSecurityRows(
      List<NavReportView> securities,
      Map<String, PortfolioCostBasisSnapshot> costBasisMap,
      BigDecimal fundNav) {
    var instrumentMap = loadInstrumentMap(securities);
    return securities.stream()
        .map(
            sec -> {
              var ref = instrumentMap.get(sec.getAccountId());
              var displayName = ref != null ? ref.getDisplayName() : sec.getAccountName();
              var manager = ref != null ? ref.getFundManager() : null;
              var country = ref != null ? ref.getCountry() : null;
              if (sec.getMarketValue() == null) {
                throw new IllegalStateException(
                    "SECURITY row has no market value, cannot build report: isin=%s, navAccount=%s"
                        .formatted(sec.getAccountId(), sec.getAccountName()));
              }
              var marketValue = sec.getMarketValue();
              var navPct =
                  fundNav.signum() != 0
                      ? marketValue.divide(fundNav, 6, RoundingMode.HALF_UP)
                      : BigDecimal.ZERO;

              var costBasis = costBasisMap.get(sec.getAccountId());
              var avgCostPerUnit = costBasis != null ? costBasis.avgUnitCost() : null;
              var avgCostTotal = costBasis != null ? costBasis.totalCost() : null;

              return new InvestmentReportRow(
                  displayName,
                  manager,
                  sec.getAccountId(),
                  country,
                  "EUR",
                  avgCostPerUnit,
                  avgCostTotal,
                  sec.getMarketPrice(),
                  marketValue,
                  navPct,
                  null);
            })
        .toList();
  }

  List<InvestmentReportRow> buildCashRows(
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

  private Map<String, InstrumentReference> loadInstrumentMap(List<NavReportView> securities) {
    return securities.stream()
        .map(NavReportView::getAccountId)
        .distinct()
        .map(instrumentReferenceService::findByIsin)
        .flatMap(Optional::stream)
        .collect(Collectors.toMap(InstrumentReference::getIsin, Function.identity()));
  }

  static @Nullable BigDecimal securitiesTotalCostIfComplete(
      List<InvestmentReportRow> securityRows) {
    if (securityRows.isEmpty()
        || securityRows.stream().anyMatch(row -> row.avgCostTotal() == null)) {
      return null;
    }
    return securityRows.stream()
        .map(InvestmentReportRow::avgCostTotal)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
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

  record RowsResult(List<InvestmentReportRow> securityRows, List<InvestmentReportRow> cashRows) {}
}
