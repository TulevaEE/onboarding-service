package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.investment.check.fee.FeeCheckScope.ALL;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckScope.DEPOT;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckScope.MANAGEMENT;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.FAIL;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.NOT_RUN;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckType.BLACKROCK_ADJUSTMENT_FRESHNESS;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckType.CASH_SETTLEMENT_OBSERVED;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckType.CUSTODIAN_POSITION_COMPLETENESS;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckType.FEE_BASE_COMPLETENESS;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckType.LEDGER_ACCRUAL_CONSISTENCY;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckType.SETTLEMENT_COMPLETENESS;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.investment.fees.FeeType;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
class FeeCheckService {

  private static final List<FeeCheckScope> PER_FEE_TYPE = List.of(MANAGEMENT, DEPOT);

  private final LedgerAccrualConsistencyChecker ledgerAccrualConsistencyChecker;
  private final FeeBaseCompletenessChecker feeBaseCompletenessChecker;
  private final CustodianCompletenessChecker custodianCompletenessChecker;
  private final BlackrockAdjustmentFreshnessChecker blackrockAdjustmentFreshnessChecker;
  private final SettlementCompletenessChecker settlementCompletenessChecker;
  private final CashSettlementChecker cashSettlementChecker;
  private final FeeCheckEventRepository eventRepository;
  private final FeeCheckNotifier notifier;
  private final int lookbackDays;

  FeeCheckService(
      LedgerAccrualConsistencyChecker ledgerAccrualConsistencyChecker,
      FeeBaseCompletenessChecker feeBaseCompletenessChecker,
      CustodianCompletenessChecker custodianCompletenessChecker,
      BlackrockAdjustmentFreshnessChecker blackrockAdjustmentFreshnessChecker,
      SettlementCompletenessChecker settlementCompletenessChecker,
      CashSettlementChecker cashSettlementChecker,
      FeeCheckEventRepository eventRepository,
      FeeCheckNotifier notifier,
      @Value("${investment.fee-check.daily-check-lookback-days:35}") int lookbackDays) {
    this.ledgerAccrualConsistencyChecker = ledgerAccrualConsistencyChecker;
    this.feeBaseCompletenessChecker = feeBaseCompletenessChecker;
    this.custodianCompletenessChecker = custodianCompletenessChecker;
    this.blackrockAdjustmentFreshnessChecker = blackrockAdjustmentFreshnessChecker;
    this.settlementCompletenessChecker = settlementCompletenessChecker;
    this.cashSettlementChecker = cashSettlementChecker;
    this.eventRepository = eventRepository;
    this.notifier = notifier;
    this.lookbackDays = lookbackDays;
  }

  @Transactional
  List<FeeCheckResult> runDailyChecks(List<TulevaFund> funds, LocalDate checkDate) {
    return run(
        funds,
        checkDate,
        null,
        fund -> dailyFindings(fund, windowStart(fund, checkDate), checkDate));
  }

  // Reaches back over whatever the run that first saw an outstanding deviation was looking at.
  // A rolling window alone lets an unfixed deviation age out, and the checker's pass on a window
  // that no longer covers the divergent date is announced as CLEARED.
  private LocalDate windowStart(TulevaFund fund, LocalDate checkDate) {
    var rollingFrom = checkDate.minusDays(lookbackDays);
    return eventRepository
        .findOldestUnresolvedDailyDeviationDate(fund)
        .map(firstSeen -> firstSeen.minusDays(lookbackDays))
        .filter(rollingFrom::isAfter)
        .orElse(rollingFrom);
  }

  // The cash leg trails the settlement leg by a month: a month settles on its last day but the
  // payment only lands weeks later, so asking about the same month would always answer NOT_RUN.
  @Transactional
  List<FeeCheckResult> runMonthlyChecks(
      List<TulevaFund> funds, LocalDate settlementMonth, LocalDate cashMonth, LocalDate checkDate) {
    var saved = new ArrayList<FeeCheckEvent>();
    var results = new ArrayList<FeeCheckResult>();
    for (var fund : funds) {
      results.add(
          resultFor(
              fund,
              checkDate,
              settlementMonth,
              monthlyFindings(fund, settlementMonth, checkDate),
              saved));
      results.add(
          resultFor(fund, checkDate, cashMonth, cashFindings(fund, cashMonth, checkDate), saved));
    }
    notifyAndRecordDelivery(results, saved);
    return results;
  }

  // A run whose alert never reached anyone must not become the baseline the next run diffs
  // against, or a deviation that first appeared during a Slack outage stays silent forever.
  private void notifyAndRecordDelivery(List<FeeCheckResult> results, List<FeeCheckEvent> saved) {
    if (notifier.notify(results) != FeeCheckNotification.SEND_FAILED) {
      return;
    }
    saved.forEach(event -> event.setAlertFailed(true));
    eventRepository.saveAll(saved);
  }

  private List<FeeCheckResult> run(
      List<TulevaFund> funds,
      LocalDate checkDate,
      @Nullable LocalDate feeMonth,
      Function<TulevaFund, List<FeeCheckFinding>> checks) {
    var saved = new ArrayList<FeeCheckEvent>();
    var results =
        funds.stream()
            .map(fund -> resultFor(fund, checkDate, feeMonth, checks.apply(fund), saved))
            .toList();
    notifyAndRecordDelivery(results, saved);
    return results;
  }

  private FeeCheckResult resultFor(
      TulevaFund fund,
      LocalDate checkDate,
      @Nullable LocalDate feeMonth,
      List<FeeCheckFinding> findings,
      List<FeeCheckEvent> saved) {
    var result = new FeeCheckResult(fund, checkDate, feeMonth, findings);
    saved.addAll(saveEvents(result));
    return result;
  }

  private List<FeeCheckFinding> cashFindings(
      TulevaFund fund, LocalDate cashMonth, LocalDate checkDate) {
    return runChecker(
        fund,
        CASH_SETTLEMENT_OBSERVED,
        List.of(MANAGEMENT),
        () -> cashSettlementChecker.check(fund, cashMonth, checkDate));
  }

  private List<FeeCheckFinding> dailyFindings(
      TulevaFund fund, LocalDate from, LocalDate checkDate) {
    var findings = new ArrayList<FeeCheckFinding>();
    for (var feeType : FeeType.values()) {
      findings.addAll(
          runChecker(
              fund,
              LEDGER_ACCRUAL_CONSISTENCY,
              List.of(scopeOf(feeType)),
              () -> ledgerAccrualConsistencyChecker.check(fund, feeType, from, checkDate)));
    }
    findings.addAll(
        runChecker(
            fund,
            FEE_BASE_COMPLETENESS,
            List.of(ALL),
            () -> feeBaseCompletenessChecker.check(fund, from, checkDate)));
    findings.addAll(
        runChecker(
            fund,
            CUSTODIAN_POSITION_COMPLETENESS,
            List.of(ALL),
            () -> custodianCompletenessChecker.check(fund, from, checkDate)));
    findings.addAll(
        runChecker(
            fund,
            BLACKROCK_ADJUSTMENT_FRESHNESS,
            List.of(ALL),
            () -> blackrockAdjustmentFreshnessChecker.check(fund, checkDate)));
    return findings;
  }

  private List<FeeCheckFinding> monthlyFindings(
      TulevaFund fund, LocalDate feeMonth, LocalDate checkDate) {
    return runChecker(
        fund,
        SETTLEMENT_COMPLETENESS,
        PER_FEE_TYPE,
        () -> settlementCompletenessChecker.check(fund, feeMonth, checkDate));
  }

  // The fallback covers the same scopes the checker would have written, so a later successful run
  // can transition them back out of NOT_RUN.
  private List<FeeCheckFinding> runChecker(
      TulevaFund fund,
      FeeCheckType checkType,
      List<FeeCheckScope> fallbackScopes,
      Supplier<List<FeeCheckFinding>> checker) {
    try {
      return checker.get();
    } catch (Exception e) {
      log.error("Fee check failed to run: fund={}, checkType={}", fund, checkType, e);
      return fallbackScopes.stream()
          .map(
              scope ->
                  new FeeCheckFinding(
                      fund,
                      checkType,
                      scope,
                      NOT_RUN,
                      "Check did not run: " + e.getClass().getSimpleName(),
                      null,
                      Map.of()))
          .toList();
    }
  }

  private List<FeeCheckEvent> saveEvents(FeeCheckResult result) {
    var saved = new ArrayList<FeeCheckEvent>();
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
        saved.add(
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
                    .build()));
      }
    }
    return saved;
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
    return feeType == FeeType.MANAGEMENT ? MANAGEMENT : DEPOT;
  }
}
