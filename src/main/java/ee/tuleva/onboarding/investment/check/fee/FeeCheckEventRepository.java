package ee.tuleva.onboarding.investment.check.fee;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface FeeCheckEventRepository extends JpaRepository<FeeCheckEvent, Long> {

  // Deliberately not scoped by check_date: the daily legs write one row per day, so a same-day
  // lookup would never find a predecessor and every persisting deviation would alert again daily.
  // Rows whose alert never reached anyone are skipped - an undelivered severity must not become
  // the baseline the next run diffs against. Ordered by id as well as createdAt, so two rows
  // written close enough together to share a timestamp can never come back in an arbitrary order.
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

  // The monthly legs get their own method rather than sharing one with a nullable argument: a null
  // parameter renders as "= ?" and would match nothing.
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

  // The first check date of the oldest deviation this fund has not cleared yet. A deviating row
  // counts as outstanding while no later run of its own check type and scope came back clean, so a
  // check type that has deviated since its very first run is included rather than skipped for
  // having no clean run to measure from.
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
            AND clean.checkDate >= event.checkDate)
      """)
  Optional<LocalDate> findOldestUnresolvedDailyDeviationDate(TulevaFund fund);
}
