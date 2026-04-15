package ee.tuleva.onboarding.investment.check.tracking;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface TrackingDifferenceEventRepository extends JpaRepository<TrackingDifferenceEvent, Long> {

  @Query(
      """
      SELECT e FROM TrackingDifferenceEvent e
      WHERE e.fund = :fund AND e.checkType = :checkType
        AND e.checkDate < :checkDate
        AND e.id = (
          SELECT MAX(e2.id) FROM TrackingDifferenceEvent e2
          WHERE e2.fund = e.fund AND e2.checkType = e.checkType AND e2.checkDate = e.checkDate
        )
      ORDER BY e.checkDate DESC
      LIMIT :limit
      """)
  List<TrackingDifferenceEvent> findMostRecentEvents(
      TulevaFund fund, TrackingCheckType checkType, LocalDate checkDate, int limit);
}
