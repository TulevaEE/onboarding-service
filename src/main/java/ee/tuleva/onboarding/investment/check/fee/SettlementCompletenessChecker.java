package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.FAIL;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.NOT_RUN;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.PASS;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckType.SETTLEMENT_COMPLETENESS;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.deadline.BusinessDays;
import ee.tuleva.onboarding.investment.fees.FeeAccrualRepository;
import ee.tuleva.onboarding.investment.fees.FeeChargedToFundPolicy;
import ee.tuleva.onboarding.investment.fees.FeeMonthBalance;
import ee.tuleva.onboarding.investment.fees.FeeType;
import ee.tuleva.onboarding.investment.fees.OutstandingFees;
import ee.tuleva.onboarding.ledger.NavFeeAccrualLedger;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class SettlementCompletenessChecker {

  private static final BigDecimal HALF_A_CENT_PER_ROUNDED_DAILY_ACCRUAL = new BigDecimal("0.005");

  private final FeeAccrualRepository feeAccrualRepository;
  private final NavFeeAccrualLedger navFeeAccrualLedger;
  private final OutstandingFees outstandingFees;
  private final FeeChargedToFundPolicy feeChargedToFundPolicy;
  private final BusinessDays businessDays;
  private final int graceBusinessDays;

  SettlementCompletenessChecker(
      FeeAccrualRepository feeAccrualRepository,
      NavFeeAccrualLedger navFeeAccrualLedger,
      OutstandingFees outstandingFees,
      FeeChargedToFundPolicy feeChargedToFundPolicy,
      BusinessDays businessDays,
      @Value("${investment.fee-check.settlement-grace-business-days:5}") int graceBusinessDays) {
    this.feeAccrualRepository = feeAccrualRepository;
    this.navFeeAccrualLedger = navFeeAccrualLedger;
    this.outstandingFees = outstandingFees;
    this.feeChargedToFundPolicy = feeChargedToFundPolicy;
    this.businessDays = businessDays;
    this.graceBusinessDays = graceBusinessDays;
  }

  List<FeeCheckFinding> check(TulevaFund fund, LocalDate feeMonth, LocalDate checkDate) {
    var nextMonth = feeMonth.plusMonths(1);
    if (!feeAccrualRepository.existsByFundAndFeeMonth(fund, nextMonth)) {
      return reportAccrualLoopHasNotCrossedTheMonth(fund, feeMonth, nextMonth, checkDate);
    }
    var findings = new ArrayList<FeeCheckFinding>();
    for (var feeType : FeeType.values()) {
      findings.addAll(checkFeeType(fund, feeMonth, feeType));
    }
    return findings;
  }

  private List<FeeCheckFinding> reportAccrualLoopHasNotCrossedTheMonth(
      TulevaFund fund, LocalDate feeMonth, LocalDate nextMonth, LocalDate checkDate) {
    var graceEnds = businessDays.nthBusinessDayOfMonth(nextMonth, graceBusinessDays);
    var stalled = !checkDate.isBefore(graceEnds);
    var message =
        stalled
            ? "Fee month "
                + feeMonth
                + " not crossed: no "
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
                    List.of("nextMonthAccrualMissing"),
                    Map.of("feeMonth", feeMonth.toString(), "graceEnds", graceEnds.toString())))
        .toList();
  }

  private List<FeeCheckFinding> checkFeeType(TulevaFund fund, LocalDate feeMonth, FeeType feeType) {
    var feeAccount = feeType.getAccrualAccount();
    var accrued = navFeeAccrualLedger.accruedIn(fund, feeMonth, feeAccount);
    var settled = navFeeAccrualLedger.settlementOf(fund, feeMonth, feeAccount).amount();
    if (outstandingFees.releasedAtMonthEnd(feeType, feeMonth)) {
      return List.of(checkReleasedMonth(fund, feeMonth, feeType, accrued, settled));
    }
    return List.of(checkHeldMonth(fund, feeMonth, feeType, accrued, settled));
  }

  private FeeCheckFinding checkReleasedMonth(
      TulevaFund fund,
      LocalDate feeMonth,
      FeeType feeType,
      BigDecimal accrued,
      BigDecimal settled) {
    var details =
        Map.<String, Object>of(
            "feeMonth",
            feeMonth.toString(),
            "accrued",
            accrued.toPlainString(),
            "settled",
            settled.toPlainString());
    if (settled.compareTo(accrued) == 0) {
      return pass(fund, feeType, details);
    }
    return finding(
        fund,
        scopeOf(feeType),
        "settledAmount",
        feeType
            + " fee month "
            + feeMonth
            + " accrued "
            + accrued.toPlainString()
            + " but was released for "
            + settled.toPlainString(),
        accrued.subtract(settled),
        details);
  }

  private FeeCheckFinding checkHeldMonth(
      TulevaFund fund,
      LocalDate feeMonth,
      FeeType feeType,
      BigDecimal accrued,
      BigDecimal settled) {
    var month = asReconciledByTheNav(fund, feeMonth, feeType);
    var navOwes = month.map(FeeMonthBalance::owed).orElse(ZERO);
    var ledgerOwes = accrued.subtract(settled);
    var details =
        Map.<String, Object>of(
            "feeMonth",
            feeMonth.toString(),
            "accrued",
            accrued.toPlainString(),
            "settled",
            settled.toPlainString(),
            "ledgerOwes",
            ledgerOwes.toPlainString(),
            "navOwes",
            navOwes.toPlainString());
    var difference = ledgerOwes.subtract(navOwes);
    if (difference.abs().compareTo(dailyRoundingAllowance(feeMonth)) > 0) {
      return finding(
          fund,
          scopeOf(feeType),
          "ledgerDiffersFromNav",
          feeType
              + " fee month "
              + feeMonth
              + ": the ledger owes "
              + ledgerOwes.toPlainString()
              + " but the NAV owes "
              + navOwes.toPlainString(),
          difference,
          details);
    }
    if (month.filter(FeeMonthBalance::hasBeenPaid).isEmpty() && accrued.signum() != 0) {
      return new FeeCheckFinding(
          fund,
          SETTLEMENT_COMPLETENESS,
          scopeOf(feeType),
          NOT_RUN,
          "Fee month " + feeMonth + " is not settled yet: no debit has paid it",
          null,
          List.of("awaitingDebit"),
          details);
    }
    return pass(fund, feeType, details);
  }

  private Optional<FeeMonthBalance> asReconciledByTheNav(
      TulevaFund fund, LocalDate feeMonth, FeeType feeType) {
    var reconciledThrough = feeAccrualRepository.findLatestAccrualDate(fund).orElseThrow();
    return outstandingFees
        .monthsOn(feeChargedToFundPolicy.resolverFor(fund, feeType), reconciledThrough)
        .stream()
        .filter(month -> month.feeMonth().equals(feeMonth))
        .findFirst();
  }

  private static BigDecimal dailyRoundingAllowance(LocalDate feeMonth) {
    return HALF_A_CENT_PER_ROUNDED_DAILY_ACCRUAL.multiply(
        BigDecimal.valueOf(feeMonth.lengthOfMonth() + 1L));
  }

  private FeeCheckFinding pass(TulevaFund fund, FeeType feeType, Map<String, Object> details) {
    return new FeeCheckFinding(
        fund, SETTLEMENT_COMPLETENESS, scopeOf(feeType), PASS, "", null, List.of(), details);
  }

  private FeeCheckFinding finding(
      TulevaFund fund,
      FeeCheckScope scope,
      String identifier,
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
        List.of(identifier),
        details);
  }

  private Stream<FeeCheckScope> scopes() {
    return Arrays.stream(FeeType.values()).map(this::scopeOf);
  }

  private FeeCheckScope scopeOf(FeeType feeType) {
    return feeType == FeeType.MANAGEMENT ? FeeCheckScope.MANAGEMENT : FeeCheckScope.DEPOT;
  }
}
