package ee.tuleva.onboarding.investment.check.fee;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface FeeCheckEventRepository extends JpaRepository<FeeCheckEvent, Long> {

  @Query(
      """
      SELECT event FROM FeeCheckEvent event
      WHERE event.fund = :fund
        AND event.checkType = :checkType
        AND event.feeScope = :feeScope
        AND event.alertFailed = false
        AND event.feeMonth IS NULL
      ORDER BY event.createdAt DESC, event.id DESC
      """)
  List<FeeCheckEvent> findLatestDelivered(
      TulevaFund fund, FeeCheckType checkType, FeeCheckScope feeScope, Limit limit);

  @Query(
      """
      SELECT event FROM FeeCheckEvent event
      WHERE event.fund = :fund
        AND event.checkType = :checkType
        AND event.feeScope = :feeScope
        AND event.alertFailed = false
        AND event.feeMonth = :feeMonth
      ORDER BY event.createdAt DESC, event.id DESC
      """)
  List<FeeCheckEvent> findLatestDeliveredForFeeMonth(
      TulevaFund fund,
      FeeCheckType checkType,
      FeeCheckScope feeScope,
      LocalDate feeMonth,
      Limit limit);

  @Query(
      """
      SELECT MIN(event.checkDate) FROM FeeCheckEvent event
      WHERE event.fund = :fund
        AND event.feeMonth IS NULL
        AND event.deviationFound = true
        AND NOT EXISTS (
          SELECT clean.id FROM FeeCheckEvent clean
          WHERE clean.fund = event.fund
            AND clean.feeMonth IS NULL
            AND clean.checkType = event.checkType
            AND clean.feeScope = event.feeScope
            AND clean.deviationFound = false
            AND clean.severity <>
                ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.NOT_RUN
            AND clean.checkDate >= event.checkDate)
      """)
  Optional<LocalDate> findOldestUnresolvedDailyDeviationDate(TulevaFund fund);
}
