package ee.tuleva.onboarding.investment.check.fee;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface FeeCheckEventRepository extends JpaRepository<FeeCheckEvent, Long> {

  // Deliberately not scoped by check_date: the daily legs write one row per day, so a same-day
  // lookup would never find a predecessor and every persisting deviation would alert again daily.
  List<FeeCheckEvent>
      findTop2ByFundAndCheckTypeAndFeeScopeAndAlertFailedFalseAndFeeMonthIsNullOrderByCreatedAtDesc(
          TulevaFund fund, FeeCheckType checkType, FeeCheckScope feeScope);

  // A null parameter renders as "= ?" and matches nothing, so the monthly legs get their own
  // method rather than sharing one with a nullable argument. Both skip rows whose alert never
  // reached anyone: an undelivered severity must not become the baseline the next run diffs on.
  List<FeeCheckEvent>
      findTop2ByFundAndCheckTypeAndFeeScopeAndAlertFailedFalseAndFeeMonthOrderByCreatedAtDesc(
          TulevaFund fund, FeeCheckType checkType, FeeCheckScope feeScope, LocalDate feeMonth);
}
