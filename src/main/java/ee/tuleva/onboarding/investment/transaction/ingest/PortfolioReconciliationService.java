package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.investment.position.SecurityQuantities;
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
import ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisRefresh;
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

  public void reconcile(TulevaFund fund, LocalDate asOfDate, CostBasisRefresh costBasisRefresh) {
    Map<String, BigDecimal> ourQuantities = readOurQuantities(fund, asOfDate);
    Map<String, BigDecimal> theirQuantities =
        navReportLookup.findSecurityQuantities(fund, asOfDate);

    if (ourQuantities.isEmpty() || theirQuantities.isEmpty()) {
      skip(fund, asOfDate, ourQuantities, theirQuantities, costBasisRefresh);
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
        new PortfolioReconciliationMismatchEvent(fund, asOfDate, mismatches, costBasisRefresh));
  }

  @Override
  public PositionReconciliationResult againstPositionReport(
      TulevaFund fund, LocalDate positionDate, Set<String> excludedIsins) {
    List<FundPosition> reportRows =
        fundPositionRepository
            .findByNavDateAndFundAndAccountType(positionDate, fund, SECURITY)
            .stream()
            .filter(position -> isNotExcluded(position, excludedIsins))
            .toList();
    List<String> unquantified = securitiesWithoutQuantity(reportRows);
    if (!unquantified.isEmpty()) {
      return new ReportRowsWithoutQuantity(unquantified);
    }
    Map<String, BigDecimal> registry = new HashMap<>(readOurQuantities(fund, positionDate));
    registry.keySet().removeAll(excludedIsins);
    Map<String, BigDecimal> report = SecurityQuantities.byIsin(reportRows);
    List<HoldingDifference> differences =
        isinsOf(registry, report).stream()
            .filter(isin -> differs(registry.get(isin), report.get(isin)))
            .map(isin -> new HoldingDifference(isin, registry.get(isin), report.get(isin)))
            .toList();
    if (differences.isEmpty()) {
      return new Compared(differences);
    }
    if (holdsNothing(registry)) {
      return new RegistryHasNoHoldings();
    }
    if (holdsNothing(report)) {
      return new ReportHasNoSecurities();
    }
    return new Compared(differences);
  }

  private static boolean holdsNothing(Map<String, BigDecimal> quantities) {
    return quantities.values().stream().allMatch(quantity -> quantity.signum() == 0);
  }

  private static boolean isNotExcluded(FundPosition position, Set<String> excludedIsins) {
    String isin = position.getAccountId();
    return isin == null || !excludedIsins.contains(isin);
  }

  private boolean differs(
      @Nullable BigDecimal registryQuantity, @Nullable BigDecimal reportQuantity) {
    if (registryQuantity == null) {
      return isHeld(reportQuantity);
    }
    if (reportQuantity == null) {
      return isHeld(registryQuantity);
    }
    return registryQuantity.subtract(reportQuantity).abs().compareTo(tolerance) > 0;
  }

  private static boolean isHeld(@Nullable BigDecimal quantity) {
    return quantity != null && quantity.signum() != 0;
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

      if (exceedsTheTolerance(delta)) {
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

  private static List<String> securitiesWithoutQuantity(List<FundPosition> reportRows) {
    return reportRows.stream()
        .filter(position -> position.getQuantity() == null)
        .map(FundPosition::getAccountId)
        .filter(Objects::nonNull)
        .distinct()
        .sorted()
        .toList();
  }

  private void skip(
      TulevaFund fund,
      LocalDate asOfDate,
      Map<String, BigDecimal> ourQuantities,
      Map<String, BigDecimal> theirQuantities,
      CostBasisRefresh costBasisRefresh) {
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
        new PortfolioReconciliationSkippedEvent(
            fund, asOfDate, ourQuantities, theirQuantities, costBasisRefresh));
  }

  private boolean exceedsTheTolerance(BigDecimal delta) {
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
