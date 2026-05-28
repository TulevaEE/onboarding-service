package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.transaction.PortfolioCostBasisService;
import ee.tuleva.onboarding.investment.transaction.PortfolioCostBasisSnapshot;
import ee.tuleva.onboarding.investment.transaction.ingest.PortfolioReconciliationMismatchEvent.MismatchEntry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioReconciliationService {

  private static final BigDecimal DEFAULT_TOLERANCE = BigDecimal.ONE;

  private final PortfolioCostBasisService costBasisService;
  private final NavReportPositionLookup navReportLookup;
  private final ApplicationEventPublisher eventPublisher;

  @Value("${transaction-registry.portfolio-reconciliation.tolerance:1.0}")
  private BigDecimal tolerance = DEFAULT_TOLERANCE;

  public void reconcile(TulevaFund fund, LocalDate asOfDate) {
    Map<String, BigDecimal> ourQuantities = readOurQuantities(fund, asOfDate);
    Map<String, BigDecimal> theirQuantities =
        navReportLookup.findSecurityQuantities(fund, asOfDate);

    Set<String> allIsins = new TreeSet<>();
    allIsins.addAll(ourQuantities.keySet());
    allIsins.addAll(theirQuantities.keySet());

    List<MismatchEntry> mismatches = new ArrayList<>();
    for (String isin : allIsins) {
      BigDecimal ourQty = ourQuantities.get(isin);
      BigDecimal theirQty = theirQuantities.get(isin);
      BigDecimal ourEffective = ourQty == null ? BigDecimal.ZERO : ourQty;
      BigDecimal theirEffective = theirQty == null ? BigDecimal.ZERO : theirQty;
      BigDecimal delta = ourEffective.subtract(theirEffective);

      if (isMismatch(ourQty, theirQty, delta)) {
        mismatches.add(new MismatchEntry(isin, ourQty, theirQty, delta));
      }
    }

    if (mismatches.isEmpty()) {
      log.info(
          "Portfolio reconciliation OK: fundCode={}, asOfDate={}, isinCount={}",
          fund.getCode(),
          asOfDate,
          allIsins.size());
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

  private boolean isMismatch(BigDecimal ourQty, BigDecimal theirQty, BigDecimal delta) {
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
