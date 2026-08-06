package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.investment.check.fee.FeeCheckScope.ALL;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.FAIL;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.NOT_RUN;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckType.BLACKROCK_ADJUSTMENT_FRESHNESS;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckType.FEE_BASE_COMPLETENESS;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckType.LEDGER_ACCRUAL_CONSISTENCY;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.fees.FeeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
class FeeCheckService {

  private final LedgerAccrualConsistencyChecker ledgerAccrualConsistencyChecker;
  private final FeeBaseCompletenessChecker feeBaseCompletenessChecker;
  private final BlackrockAdjustmentFreshnessChecker blackrockAdjustmentFreshnessChecker;
  private final FeeCheckEventRepository eventRepository;
  private final FeeCheckNotifier notifier;
  private final int lookbackDays;

  FeeCheckService(
      LedgerAccrualConsistencyChecker ledgerAccrualConsistencyChecker,
      FeeBaseCompletenessChecker feeBaseCompletenessChecker,
      BlackrockAdjustmentFreshnessChecker blackrockAdjustmentFreshnessChecker,
      FeeCheckEventRepository eventRepository,
      FeeCheckNotifier notifier,
      @Value("${investment.fee-check.daily-check-lookback-days:35}") int lookbackDays) {
    this.ledgerAccrualConsistencyChecker = ledgerAccrualConsistencyChecker;
    this.feeBaseCompletenessChecker = feeBaseCompletenessChecker;
    this.blackrockAdjustmentFreshnessChecker = blackrockAdjustmentFreshnessChecker;
    this.eventRepository = eventRepository;
    this.notifier = notifier;
    this.lookbackDays = lookbackDays;
  }

  @Transactional
  List<FeeCheckResult> runDailyChecks(List<TulevaFund> funds, LocalDate checkDate) {
    var from = checkDate.minusDays(lookbackDays);
    var results = funds.stream().map(fund -> checkFund(fund, from, checkDate)).toList();
    notifier.notify(results);
    return results;
  }

  private FeeCheckResult checkFund(TulevaFund fund, LocalDate from, LocalDate checkDate) {
    var findings = new ArrayList<FeeCheckFinding>();
    for (var feeType : FeeType.values()) {
      findings.addAll(
          runChecker(
              fund,
              LEDGER_ACCRUAL_CONSISTENCY,
              scopeOf(feeType),
              () -> ledgerAccrualConsistencyChecker.check(fund, feeType, from, checkDate)));
    }
    findings.addAll(
        runChecker(
            fund,
            FEE_BASE_COMPLETENESS,
            ALL,
            () -> feeBaseCompletenessChecker.check(fund, from, checkDate)));
    findings.addAll(
        runChecker(
            fund,
            BLACKROCK_ADJUSTMENT_FRESHNESS,
            ALL,
            () -> blackrockAdjustmentFreshnessChecker.check(fund, checkDate)));

    var result = new FeeCheckResult(fund, checkDate, null, findings);
    saveEvents(result);
    return result;
  }

  // One checker blowing up must not take the others down with it - being blind about one thing is
  // not a reason to stop checking everything else.
  private List<FeeCheckFinding> runChecker(
      TulevaFund fund,
      FeeCheckType checkType,
      FeeCheckScope scope,
      Supplier<List<FeeCheckFinding>> checker) {
    try {
      return checker.get();
    } catch (Exception e) {
      log.error(
          "Fee check failed to run: fund={}, checkType={}, scope={}", fund, checkType, scope, e);
      return List.of(
          new FeeCheckFinding(
              fund,
              checkType,
              scope,
              NOT_RUN,
              "Check did not run: " + e.getClass().getSimpleName(),
              null,
              Map.of()));
    }
  }

  private void saveEvents(FeeCheckResult result) {
    for (var checkType : FeeCheckType.values()) {
      for (var scope : FeeCheckScope.values()) {
        var findings =
            result.findings().stream()
                .filter(f -> f.checkType() == checkType && f.scope() == scope)
                .toList();
        if (findings.isEmpty()) {
          continue;
        }
        var severity = findings.stream().map(FeeCheckFinding::severity).max(Enum::compareTo).get();
        eventRepository.save(
            FeeCheckEvent.builder()
                .fund(result.fund())
                .checkDate(result.checkDate())
                .feeMonth(result.feeMonth())
                .checkType(checkType)
                .feeScope(scope)
                .severity(severity)
                .deviationFound(severity == WARNING || severity == FAIL)
                .deviationAmount(totalDeviation(findings))
                .result(Map.of("findings", findings.stream().map(this::describe).toList()))
                .build());
      }
    }
  }

  private BigDecimal totalDeviation(List<FeeCheckFinding> findings) {
    return findings.stream()
        .map(FeeCheckFinding::deviationAmount)
        .filter(Objects::nonNull)
        .map(BigDecimal::abs)
        .reduce(ZERO, BigDecimal::add);
  }

  private Map<String, Object> describe(FeeCheckFinding finding) {
    return Map.of(
        "severity",
        finding.severity().name(),
        "message",
        finding.message(),
        "details",
        finding.details());
  }

  private FeeCheckScope scopeOf(FeeType feeType) {
    return feeType == FeeType.MANAGEMENT ? FeeCheckScope.MANAGEMENT : FeeCheckScope.DEPOT;
  }
}
