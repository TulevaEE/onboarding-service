package ee.tuleva.onboarding.investment.check.tracking;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;

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
    List<SecurityAttribution> securityAttributions,
    BigDecimal cashDrag,
    BigDecimal feeDrag,
    BigDecimal residual) {}
