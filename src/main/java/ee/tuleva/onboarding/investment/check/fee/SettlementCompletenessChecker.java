package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.FAIL;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.NOT_RUN;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckType.SETTLEMENT_COMPLETENESS;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.FEE_ACCRUAL;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.FEE_SETTLEMENT;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.deadline.BusinessDays;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.fees.FeeAccrualRepository;
import ee.tuleva.onboarding.investment.fees.FeeType;
import ee.tuleva.onboarding.ledger.LedgerEntryAmount;
import ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Settlement sweeps the whole outstanding balance rather than a computed amount, so every invariant
// here is an exact identity between two-decimal quantities from one code path. A tolerance on any
// of them could only ever hide a real defect.
@Component
class SettlementCompletenessChecker {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  private final FeeAccrualRepository feeAccrualRepository;
  private final NavLedgerRepository navLedgerRepository;
  private final BusinessDays businessDays;
  private final int graceBusinessDays;

  SettlementCompletenessChecker(
      FeeAccrualRepository feeAccrualRepository,
      NavLedgerRepository navLedgerRepository,
      BusinessDays businessDays,
      @Value("${investment.fee-check.settlement-grace-business-days:5}") int graceBusinessDays) {
    this.feeAccrualRepository = feeAccrualRepository;
    this.navLedgerRepository = navLedgerRepository;
    this.businessDays = businessDays;
    this.graceBusinessDays = graceBusinessDays;
  }

  List<FeeCheckFinding> check(TulevaFund fund, LocalDate feeMonth, LocalDate checkDate) {
    var nextMonth = feeMonth.plusMonths(1);
    if (!feeAccrualRepository.existsByFundAndFeeMonth(fund, nextMonth)) {
      return monthNotCrossed(fund, feeMonth, nextMonth, checkDate);
    }
    var findings = new ArrayList<FeeCheckFinding>();
    for (var feeType : FeeType.values()) {
      findings.addAll(checkFeeType(fund, feeMonth, feeType));
    }
    return findings;
  }

  // Settlement only ever fires from inside the accrual loop, so a stalled NAV pipeline is exactly
  // the case where settlement never happens. An unbounded precondition would stay quiet forever
  // about the one failure it most needs to report.
  private List<FeeCheckFinding> monthNotCrossed(
      TulevaFund fund, LocalDate feeMonth, LocalDate nextMonth, LocalDate checkDate) {
    var graceEnds = businessDays.nthBusinessDayOfMonth(nextMonth, graceBusinessDays);
    var stalled = !checkDate.isBefore(graceEnds);
    var message =
        stalled
            ? "Month-end settlement has not run for "
                + feeMonth
                + ": no "
                + nextMonth
                + " accrual by "
                + graceEnds
                + ", the NAV pipeline appears stalled for this fund"
            : "Fee month " + feeMonth + " not yet crossed: no accrual for " + nextMonth + " yet";
    return scopes()
        .map(
            scope ->
                new FeeCheckFinding(
                    fund,
                    SETTLEMENT_COMPLETENESS,
                    scope,
                    stalled ? WARNING : NOT_RUN,
                    message,
                    null,
                    Map.of("feeMonth", feeMonth.toString(), "graceEnds", graceEnds.toString())))
        .toList();
  }

  private List<FeeCheckFinding> checkFeeType(TulevaFund fund, LocalDate feeMonth, FeeType feeType) {
    var account = feeType.getAccrualAccount().getAccountName(fund);
    var from = startOf(feeMonth);
    var to = startOf(feeMonth.plusMonths(1));

    var opening = navLedgerRepository.getSystemAccountBalanceBefore(account, from);
    var closing = navLedgerRepository.getSystemAccountBalanceBefore(account, to);
    var accrued = sum(entries(account, FEE_ACCRUAL, from, to));
    var settlements = entries(account, FEE_SETTLEMENT, from, to);
    var settled = sum(settlements);
    var expectedSettlement = opening.add(accrued).negate();

    var details = details(feeMonth, opening, closing, accrued, settled);
    var scope = scopeOf(feeType);
    var findings = new ArrayList<FeeCheckFinding>();

    if (opening.signum() != 0) {
      findings.add(
          finding(
              fund,
              scope,
              "Fee month "
                  + feeMonth
                  + " opened with a non-zero "
                  + feeType
                  + " accrual balance of "
                  + opening.toPlainString()
                  + ", so a correction to "
                  + feeMonth.minusMonths(1)
                  + " is being settled against this month instead",
              opening,
              details));
    }
    if (closing.signum() != 0) {
      findings.add(
          finding(
              fund,
              scope,
              "Fee month "
                  + feeMonth
                  + " left a "
                  + feeType
                  + " residual of "
                  + closing.toPlainString()
                  + " on the accrual account after settlement",
              closing,
              details));
    }
    if (settled.compareTo(expectedSettlement) != 0) {
      findings.add(
          finding(
              fund,
              scope,
              "Settled "
                  + feeType
                  + " fees for "
                  + feeMonth
                  + " were "
                  + settled.toPlainString()
                  + " but the accrued balance to settle was "
                  + expectedSettlement.toPlainString(),
              settled.subtract(expectedSettlement),
              details));
    }
    var expectedCount = expectedSettlement.signum() > 0 ? 1 : 0;
    var actualCount = settlements.stream().map(LedgerEntryAmount::transactionId).distinct().count();
    if (actualCount != expectedCount) {
      findings.add(
          finding(
              fund,
              scope,
              "Expected "
                  + expectedCount
                  + " "
                  + feeType
                  + " settlement transaction(s) for "
                  + feeMonth
                  + " but found "
                  + actualCount,
              null,
              details));
    }

    if (findings.isEmpty()) {
      return List.of(
          new FeeCheckFinding(
              fund, SETTLEMENT_COMPLETENESS, scope, FeeCheckSeverity.PASS, "", null, details));
    }
    return findings;
  }

  private Map<String, Object> details(
      LocalDate feeMonth,
      BigDecimal opening,
      BigDecimal closing,
      BigDecimal accrued,
      BigDecimal settled) {
    var details = new HashMap<String, Object>();
    details.put("feeMonth", feeMonth.toString());
    details.put("opening", opening.toPlainString());
    details.put("closing", closing.toPlainString());
    details.put("accrued", accrued.toPlainString());
    details.put("settled", settled.toPlainString());
    return Map.copyOf(details);
  }

  private FeeCheckFinding finding(
      TulevaFund fund,
      FeeCheckScope scope,
      String message,
      @Nullable BigDecimal deviation,
      Map<String, Object> details) {
    return new FeeCheckFinding(
        fund,
        SETTLEMENT_COMPLETENESS,
        scope,
        FAIL,
        message,
        deviation == null ? null : deviation.abs(),
        details);
  }

  private List<LedgerEntryAmount> entries(
      String account, TransactionType transactionType, Instant from, Instant to) {
    return navLedgerRepository.findEntriesByTransactionTypeBetween(
        account, transactionType, from, to);
  }

  private BigDecimal sum(List<LedgerEntryAmount> entries) {
    return entries.stream().map(LedgerEntryAmount::amount).reduce(ZERO, BigDecimal::add);
  }

  private Instant startOf(LocalDate month) {
    return month.atStartOfDay(ESTONIAN_ZONE).toInstant();
  }

  private Stream<FeeCheckScope> scopes() {
    return Arrays.stream(FeeType.values()).map(this::scopeOf);
  }

  private FeeCheckScope scopeOf(FeeType feeType) {
    return feeType == FeeType.MANAGEMENT ? FeeCheckScope.MANAGEMENT : FeeCheckScope.DEPOT;
  }
}
