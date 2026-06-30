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
    @Nullable BigDecimal impliedFundReturn,
    @Nullable BigDecimal navResidual,
    boolean navResidualBreach,
    boolean escalationNavResidualBreach) {}
