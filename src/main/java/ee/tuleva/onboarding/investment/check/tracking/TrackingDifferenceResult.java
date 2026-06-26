package ee.tuleva.onboarding.investment.check.tracking;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

@Builder(toBuilder = true)
record TrackingDifferenceResult(
    TulevaFund fund,
    LocalDate checkDate,
    TrackingCheckType checkType,
    BigDecimal trackingDifference,
    BigDecimal fundReturn,
    BigDecimal benchmarkReturn,
    boolean breach,
    int consecutiveBreachDays,
    BigDecimal consecutiveNetTd,
    BigDecimal compoundedFundReturn,
    BigDecimal compoundedBenchmarkReturn,
    Map<String, BigDecimal> escalationAttributions,
    BigDecimal escalationCashDrag,
    BigDecimal escalationFeeDrag,
    BigDecimal escalationResidual,
    List<SecurityAttribution> securityAttributions,
    BigDecimal cashDrag,
    BigDecimal feeDrag,
    BigDecimal residual,
    // NAV-correctness view: fund NAV return vs the return implied by the holdings the fund actually
    // held entering the day (begin-of-day / yesterday's EOD snapshot). Neutralises MOC trade-day
    // timing so the NAV gate keys on navResidualBreach, not on the informational fund-vs-model TD.
    @Nullable BigDecimal impliedFundReturn,
    @Nullable BigDecimal navResidual,
    boolean navResidualBreach,
    // True when any day in the current consecutive-breach streak (including today) tripped the
    // NAV-correctness residual, so escalation fires on a navResidual-only run even when the
    // compounded fund-vs-model TD stays below the net-TD threshold.
    boolean escalationNavResidualBreach) {}
