package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.PASS;
import static ee.tuleva.onboarding.investment.position.AccountType.*;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
  private final ReceivablesChecker receivablesChecker;
  private final PayablesChecker payablesChecker;

  public List<HealthCheckResult> check(List<FundPosition> positions) {
    Map<TulevaFund, List<FundPosition>> byFund =
        positions.stream().collect(Collectors.groupingBy(FundPosition::getFund));

    var results = new ArrayList<HealthCheckResult>();
    for (var entry : byFund.entrySet()) {
      var fund = entry.getKey();
      var fundPositions = entry.getValue();
      var result = checkFund(fund, fundPositions);
      results.add(result);
    }
    return results;
  }

  private HealthCheckResult checkFund(TulevaFund fund, List<FundPosition> positions) {
    var navDate = positions.getFirst().getNavDate();

    var securities = filterByType(positions, SECURITY);
    var unitsPositions = filterByType(positions, UNITS);
    var receivables = filterByType(positions, RECEIVABLES);
    var liabilities = filterByType(positions, LIABILITY);
    var allocations = modelPortfolioAllocationRepository.findLatestByFund(fund);

    var previousNavDate =
        fundPositionRepository.findLatestNavDateByFundAndAsOfDate(fund, navDate.minusDays(1));
    var previousSecurities =
        previousNavDate
            .map(
                date ->
                    fundPositionRepository.findByNavDateAndFundAndAccountType(date, fund, SECURITY))
            .orElse(List.of());

    var findings = new ArrayList<HealthCheckFinding>();
    findings.addAll(completenessChecker.check(fund, navDate, positions));
    findings.addAll(isinMatchChecker.check(fund, securities, allocations));
    findings.addAll(outstandingUnitsChecker.check(fund, navDate, unitsPositions));
    findings.addAll(receivablesChecker.check(fund, securities, previousSecurities, receivables));
    findings.addAll(payablesChecker.check(fund, securities, previousSecurities, liabilities));

    saveEvents(fund, navDate, findings);

    return new HealthCheckResult(fund, navDate, findings);
  }

  private List<FundPosition> filterByType(
      List<FundPosition> positions, ee.tuleva.onboarding.investment.position.AccountType type) {
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
