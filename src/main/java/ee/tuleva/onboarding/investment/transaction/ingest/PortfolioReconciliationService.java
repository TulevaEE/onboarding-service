package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static java.math.BigDecimal.ZERO;
import static java.util.Objects.requireNonNullElse;

import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.investment.transaction.PortfolioCostBasisService;
import ee.tuleva.onboarding.investment.transaction.PortfolioCostBasisSnapshot;
import ee.tuleva.onboarding.investment.transaction.PositionReconciliation;
import ee.tuleva.onboarding.investment.transaction.PositionReconciliationResult;
import ee.tuleva.onboarding.investment.transaction.PositionReconciliationResult.Compared;
import ee.tuleva.onboarding.investment.transaction.PositionReconciliationResult.HoldingDifference;
import ee.tuleva.onboarding.investment.transaction.PositionReconciliationResult.RegistryHasNoHoldings;
import ee.tuleva.onboarding.investment.transaction.PositionReconciliationResult.ReportHasNoSecurities;
import ee.tuleva.onboarding.investment.transaction.PositionReconciliationResult.ReportRowsWithoutQuantity;
import ee.tuleva.onboarding.investment.transaction.ingest.PortfolioReconciliationMismatchEvent.MismatchEntry;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioReconciliationService implements PositionReconciliation {

  private static final BigDecimal DEFAULT_TOLERANCE = BigDecimal.ONE;

  private final PortfolioCostBasisService costBasisService;
  private final NavReportPositionLookup navReportLookup;
  private final ApplicationEventPublisher eventPublisher;
  private final FundPositionRepository fundPositionRepository;

  @Value("${transaction-registry.portfolio-reconciliation.tolerance:1.0}")
  private BigDecimal tolerance = DEFAULT_TOLERANCE;

  public void reconcile(TulevaFund fund, LocalDate asOfDate) {
    Map<String, BigDecimal> ourQuantities = readOurQuantities(fund, asOfDate);
    Map<String, BigDecimal> theirQuantities =
        navReportLookup.findSecurityQuantities(fund, asOfDate);

    if (ourQuantities.isEmpty() || theirQuantities.isEmpty()) {
      skip(fund, asOfDate, ourQuantities, theirQuantities);
      return;
    }

    List<MismatchEntry> mismatches = mismatchesBetween(ourQuantities, theirQuantities);

    if (mismatches.isEmpty()) {
      log.info(
          "Portfolio reconciliation OK: fundCode={}, asOfDate={}, isinCount={}",
          fund.getCode(),
          asOfDate,
          isinsOf(ourQuantities, theirQuantities).size());
      return;
    }

    log.warn(
        "Portfolio reconciliation mismatches: fundCode={}, asOfDate={}, mismatchCount={}",
        fund.getCode(),
        asOfDate,
        mismatches.size());
    eventPublisher.publishEvent(
        new PortfolioReconciliationMismatchEvent(fund, asOfDate, mismatches));
  }

  @Override
  public PositionReconciliationResult againstPositionReport(
      TulevaFund fund, LocalDate positionDate) {
    List<FundPosition> reportRows =
        fundPositionRepository.findByNavDateAndFundAndAccountType(positionDate, fund, SECURITY);
    List<String> unquantified = securitiesWithoutQuantity(reportRows);
    if (!unquantified.isEmpty()) {
      return new ReportRowsWithoutQuantity(unquantified);
    }
    Map<String, BigDecimal> registry = readOurQuantities(fund, positionDate);
    Map<String, BigDecimal> report = quantitiesByIsin(reportRows);
    if (registry.isEmpty() && !report.isEmpty()) {
      return new RegistryHasNoHoldings();
    }
    if (report.isEmpty() && !registry.isEmpty()) {
      return new ReportHasNoSecurities();
    }
    return new Compared(
        mismatchesBetween(registry, report).stream()
            .map(PortfolioReconciliationService::toDifference)
            .toList());
  }

  private List<MismatchEntry> mismatchesBetween(
      Map<String, BigDecimal> ourQuantities, Map<String, BigDecimal> theirQuantities) {
    List<MismatchEntry> mismatches = new ArrayList<>();
    for (String isin : isinsOf(ourQuantities, theirQuantities)) {
      BigDecimal ourQty = ourQuantities.get(isin);
      BigDecimal theirQty = theirQuantities.get(isin);
      BigDecimal ourEffective = ourQty == null ? ZERO : ourQty;
      BigDecimal theirEffective = theirQty == null ? ZERO : theirQty;
      BigDecimal delta = ourEffective.subtract(theirEffective);

      if (isMismatch(ourQty, theirQty, delta)) {
        mismatches.add(new MismatchEntry(isin, ourQty, theirQty, delta));
      }
    }
    return mismatches;
  }

  private static Set<String> isinsOf(
      Map<String, BigDecimal> ourQuantities, Map<String, BigDecimal> theirQuantities) {
    Set<String> allIsins = new TreeSet<>();
    allIsins.addAll(ourQuantities.keySet());
    allIsins.addAll(theirQuantities.keySet());
    return allIsins;
  }

  private static HoldingDifference toDifference(MismatchEntry mismatch) {
    return new HoldingDifference(
        mismatch.isin(),
        requireNonNullElse(mismatch.ourQuantity(), ZERO),
        requireNonNullElse(mismatch.theirQuantity(), ZERO));
  }

  private static List<String> securitiesWithoutQuantity(List<FundPosition> reportRows) {
    return reportRows.stream()
        .filter(position -> position.getQuantity() == null)
        .map(FundPosition::getAccountId)
        .filter(Objects::nonNull)
        .distinct()
        .sorted()
        .toList();
  }

  private static Map<String, BigDecimal> quantitiesByIsin(List<FundPosition> reportRows) {
    Map<String, BigDecimal> quantities = new HashMap<>();
    reportRows.forEach(
        position -> {
          String isin = position.getAccountId();
          BigDecimal quantity = position.getQuantity();
          if (isin != null && quantity != null) {
            quantities.merge(isin, quantity, BigDecimal::add);
          }
        });
    return quantities;
  }

  private void skip(
      TulevaFund fund,
      LocalDate asOfDate,
      Map<String, BigDecimal> ourQuantities,
      Map<String, BigDecimal> theirQuantities) {
    if (ourQuantities.isEmpty() && theirQuantities.isEmpty()) {
      log.warn(
          "Portfolio reconciliation had nothing to compare: fundCode={}, asOfDate={}",
          fund.getCode(),
          asOfDate);
      return;
    }
    log.warn(
        "Portfolio reconciliation skipped, one side is empty: fundCode={}, asOfDate={}, ourIsinCount={}, theirIsinCount={}",
        fund.getCode(),
        asOfDate,
        ourQuantities.size(),
        theirQuantities.size());
    eventPublisher.publishEvent(
        new PortfolioReconciliationSkippedEvent(fund, asOfDate, ourQuantities, theirQuantities));
  }

  private boolean isMismatch(
      @Nullable BigDecimal ourQty, @Nullable BigDecimal theirQty, BigDecimal delta) {
    if (ourQty == null || theirQty == null) {
      return true;
    }
    return delta.abs().compareTo(tolerance) > 0;
  }

  private Map<String, BigDecimal> readOurQuantities(TulevaFund fund, LocalDate asOfDate) {
    List<PortfolioCostBasisSnapshot> snapshots =
        costBasisService.snapshotForFundAndDate(fund, asOfDate);
    Map<String, BigDecimal> result = new HashMap<>();
    snapshots.forEach(snapshot -> result.put(snapshot.instrumentIsin(), snapshot.quantity()));
    return result;
  }

  void setTolerance(BigDecimal tolerance) {
    this.tolerance = tolerance;
  }
}
