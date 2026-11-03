package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.PASS;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.NAV_FLOW_CONSISTENCY_THRESHOLD;
import static ee.tuleva.onboarding.investment.position.AccountType.*;

import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.position.AccountType;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.investment.transaction.ExecutedPrice;
import ee.tuleva.onboarding.investment.transaction.ExecutedPriceSource;
import ee.tuleva.onboarding.investment.transaction.OpenTradeSource;
import ee.tuleva.onboarding.investment.transaction.OpenTrades;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HealthCheckService {

  private final FundPositionRepository fundPositionRepository;
  private final ModelPortfolioAllocationRepository modelPortfolioAllocationRepository;
  private final HealthCheckEventRepository healthCheckEventRepository;
  private final CompletenessChecker completenessChecker;
  private final IsinMatchChecker isinMatchChecker;
  private final OutstandingUnitsChecker outstandingUnitsChecker;
  private final UnitReconciliationChecker unitReconciliationChecker;
  private final NavUnitImpactChecker navUnitImpactChecker;
  private final UnitReconciliationThresholdRepository unitReconciliationThresholdRepository;
  private final AuthoritativeUnitsSource authoritativeUnitsSource;
  private final ReceivablesChecker receivablesChecker;
  private final QuantityChangeChecker quantityChangeChecker;
  private final PayablesChecker payablesChecker;
  private final NavFlowConsistencyChecker navFlowConsistencyChecker;
  private final LiabilityRecognitionChecker liabilityRecognitionChecker;
  private final InvestmentParameterRepository investmentParameterRepository;
  private final ExecutedPriceSource executedPriceSource;
  private final ExitMarkResolver exitMarkResolver;
  private final OpenTradeSource openTradeSource;
  private final UnsettledTradesChecker unsettledTradesChecker;
  private final BankBalanceChecker bankBalanceChecker;

  private @Nullable BigDecimal navFlowThreshold(LocalDate navDate) {
    return investmentParameterRepository
        .findLatestValueIfPresent(NAV_FLOW_CONSISTENCY_THRESHOLD, navDate)
        .orElse(null);
  }

  public List<HealthCheckResult> check(List<FundPosition> positions) {
    if (positions.isEmpty()) {
      return List.of();
    }
    Map<TulevaFund, List<FundPosition>> byFund =
        positions.stream().collect(Collectors.groupingBy(FundPosition::getFund));
    var navDate = positions.getFirst().getNavDate();
    var executedSellsByFund = executedPriceSource.executedSellPricesByFund(navDate);
    Map<LocalDate, Optional<OpenTrades>> tradeListsByDate = new HashMap<>();

    var results = new ArrayList<HealthCheckResult>();
    for (var entry : byFund.entrySet()) {
      var fund = entry.getKey();
      var fundPositions = entry.getValue();
      var result =
          checkFund(
              fund,
              fundPositions,
              executedSellsByFund.getOrDefault(fund, Map.of()),
              tradeListsByDate);
      results.add(result);
    }
    return results;
  }

  private HealthCheckResult checkFund(
      TulevaFund fund,
      List<FundPosition> positions,
      Map<String, ExecutedPrice> executedSells,
      Map<LocalDate, Optional<OpenTrades>> tradeListsByDate) {
    var navDate = positions.getFirst().getNavDate();

    var securities = filterByType(positions, SECURITY);
    var unitsPositions = filterByType(positions, UNITS);
    var receivables = filterByType(positions, RECEIVABLES);
    var liabilities = filterByType(positions, LIABILITY);
    var allocations = modelPortfolioAllocationRepository.findLatestByFundAsOf(fund, navDate);
    var previousAllocations =
        modelPortfolioAllocationRepository.findPreviousByFundAsOf(fund, navDate);

    var previousNavDate =
        fundPositionRepository.findLatestNavDateByFundAndAsOfDate(fund, navDate.minusDays(1));
    var previousSecurities =
        previousNavDate
            .map(
                date ->
                    fundPositionRepository.findByNavDateAndFundAndAccountType(date, fund, SECURITY))
            .orElse(List.of());
    var previousLiabilities =
        previousNavDate
            .map(
                date ->
                    fundPositionRepository.findByNavDateAndFundAndAccountType(
                        date, fund, LIABILITY))
            .orElse(List.of());
    var previousReceivables =
        previousNavDate
            .map(
                date ->
                    fundPositionRepository.findByNavDateAndFundAndAccountType(
                        date, fund, RECEIVABLES))
            .orElse(List.of());

    var previousPositions =
        previousNavDate
            .map(date -> fundPositionRepository.findByNavDateAndFund(date, fund))
            .orElse(List.of());

    var tradeList =
        tradeListsByDate.computeIfAbsent(navDate, openTradeSource::openTrades).orElse(null);
    var previousTradeList =
        previousNavDate
            .flatMap(date -> tradeListsByDate.computeIfAbsent(date, openTradeSource::openTrades))
            .orElse(null);
    var tradeChanges =
        previousNavDate
            .map(
                date -> OpenTradeChanges.between(fund, date, previousTradeList, navDate, tradeList))
            .orElseGet(
                () ->
                    OpenTradeChanges.unavailable(
                        "no position report before %s".formatted(navDate)));

    var threshold = unitReconciliationThresholdRepository.findByFundCode(fund).orElse(null);
    var authoritativeUnits = authoritativeUnitsSource.resolve(fund, navDate).orElse(null);

    var reportedUnits = unitsPositions.isEmpty() ? null : unitsPositions.getFirst().getQuantity();
    var aum = PositionTotals.aum(positions);
    var unitsFromCash =
        UnitsFromCash.of(fund, positions, previousPositions, tradeChanges).orElse(null);

    var findings = new ArrayList<HealthCheckFinding>();
    findings.addAll(completenessChecker.check(fund, navDate, positions));
    findings.addAll(isinMatchChecker.check(fund, securities, allocations, previousAllocations));
    findings.addAll(outstandingUnitsChecker.check(fund, navDate, unitsPositions));
    findings.addAll(
        unitReconciliationChecker.check(
            fund, navDate, unitsPositions, authoritativeUnits, threshold, unitsFromCash));
    findings.addAll(
        navUnitImpactChecker.check(fund, reportedUnits, authoritativeUnits, aum, unitsFromCash));
    findings.addAll(
        receivablesChecker.check(
            fund, securities, previousSecurities, receivables, previousReceivables));
    findings.addAll(
        payablesChecker.check(
            fund, securities, previousSecurities, liabilities, previousLiabilities));
    findings.addAll(
        unsettledTradesChecker.check(
            fund, navDate, liabilities, receivables, tradeList, previousTradeList));
    findings.addAll(liabilityRecognitionChecker.check(fund, navDate, liabilities));
    findings.addAll(bankBalanceChecker.check(fund, navDate, filterByType(positions, CASH)));
    findings.addAll(
        quantityChangeChecker.check(fund, securities, previousSecurities, tradeChanges));
    findings.addAll(
        navFlowConsistencyChecker.check(
            fund,
            positions,
            previousPositions,
            navFlowThreshold(navDate),
            exitMarkResolver.resolve(navDate, executedSells)));

    saveEvents(fund, navDate, findings);

    return new HealthCheckResult(fund, navDate, findings);
  }

  private List<FundPosition> filterByType(List<FundPosition> positions, AccountType type) {
    return positions.stream().filter(p -> p.getAccountType() == type).toList();
  }

  private void saveEvents(TulevaFund fund, LocalDate checkDate, List<HealthCheckFinding> findings) {
    for (var checkType : HealthCheckType.values()) {
      var checkFindings = findings.stream().filter(f -> f.checkType() == checkType).toList();
      var severity = maxSeverity(checkFindings);
      var issuesFound = severity != PASS;

      var event =
          HealthCheckEvent.builder()
              .fund(fund)
              .checkDate(checkDate)
              .checkType(checkType)
              .issuesFound(issuesFound)
              .severity(severity)
              .result(Map.of("findings", checkFindings))
              .build();

      healthCheckEventRepository.save(event);
    }
  }

  private HealthCheckSeverity maxSeverity(List<HealthCheckFinding> findings) {
    return findings.stream().map(HealthCheckFinding::severity).max(Enum::compareTo).orElse(PASS);
  }
}
