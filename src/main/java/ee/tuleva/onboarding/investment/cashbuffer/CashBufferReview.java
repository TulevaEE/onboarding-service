package ee.tuleva.onboarding.investment.cashbuffer;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.time.YearMonth;
import org.jspecify.annotations.Nullable;

record CashBufferReview(
    TulevaFund fund,
    YearMonth reviewMonth,
    LocalDate reviewedOn,
    FlowWindow window,
    Recommendation recommendation,
    ConfiguredReserve configured,
    Drift softDrift,
    @Nullable Drift hardDrift) {

  MonthlyFlows reviewMonthFlows() {
    return window.months().getLast();
  }

  boolean driftSustained() {
    return softDrift.sustained() || (hardDrift != null && hardDrift.sustained());
  }
}
