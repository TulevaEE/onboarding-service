package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.investment.check.fee.FeeCheckType.ACCRUAL_ROUNDING_DRIFT;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.fees.FeeAccrualRepository;
import ee.tuleva.onboarding.investment.fees.FeeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// The published NAV rounds the monthly sum while the ledger sums daily rounded amounts, so the two
// are structurally different numbers and a small drift is expected every month by construction.
// Only a drift larger than daily rounding can explain means one of the two moved for another
// reason. Which side should be authoritative is a separate decision, not this check's to make.
@Component
class AccrualRoundingDriftChecker {

  private final FeeAccrualRepository feeAccrualRepository;
  private final BigDecimal roundingDriftTolerance;

  AccrualRoundingDriftChecker(
      FeeAccrualRepository feeAccrualRepository,
      @Value("${investment.fee-check.rounding-drift-tolerance:0.25}")
          BigDecimal roundingDriftTolerance) {
    this.feeAccrualRepository = feeAccrualRepository;
    this.roundingDriftTolerance = roundingDriftTolerance;
  }

  List<FeeCheckFinding> check(TulevaFund fund, LocalDate feeMonth) {
    var findings = new ArrayList<FeeCheckFinding>();
    for (var feeType : FeeType.values()) {
      var ledgerSide = feeAccrualRepository.sumRoundedDailyNetForMonth(fund, feeMonth, feeType);
      var navSide = feeAccrualRepository.roundedSumOfDailyNetForMonth(fund, feeMonth, feeType);
      var drift = navSide.subtract(ledgerSide);
      var scope = scopeOf(feeType);
      var details =
          Map.<String, Object>of(
              "feeMonth", feeMonth.toString(),
              "sumOfRoundedDailyAmounts", ledgerSide.toPlainString(),
              "roundedSumOfDailyAmounts", navSide.toPlainString(),
              "drift", drift.toPlainString());

      if (drift.abs().compareTo(roundingDriftTolerance) > 0) {
        findings.add(
            new FeeCheckFinding(
                fund,
                ACCRUAL_ROUNDING_DRIFT,
                scope,
                FeeCheckSeverity.WARNING,
                "The "
                    + feeType
                    + " fees settled for "
                    + feeMonth
                    + " and the ones charged in the NAV differ by "
                    + drift.toPlainString()
                    + ", which is more than daily rounding can explain",
                drift.abs(),
                details));
      } else {
        findings.add(
            new FeeCheckFinding(
                fund, ACCRUAL_ROUNDING_DRIFT, scope, FeeCheckSeverity.PASS, "", null, details));
      }
    }
    return findings;
  }

  private FeeCheckScope scopeOf(FeeType feeType) {
    return feeType == FeeType.MANAGEMENT ? FeeCheckScope.MANAGEMENT : FeeCheckScope.DEPOT;
  }
}
