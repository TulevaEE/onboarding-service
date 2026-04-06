package ee.tuleva.onboarding.investment.check.tracking;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

interface TrackingDifferenceEventRepository extends JpaRepository<TrackingDifferenceEvent, Long> {

  @Modifying
  @Transactional
  void deleteByFundAndCheckDateAndCheckType(
      TulevaFund fund, LocalDate checkDate, TrackingCheckType checkType);

  @Query(
      """
      SELECT e FROM TrackingDifferenceEvent e
      WHERE e.fund = :fund AND e.checkType = :checkType
        AND e.checkDate < :checkDate
      ORDER BY e.checkDate DESC
      LIMIT :limit
      """)
  List<TrackingDifferenceEvent> findMostRecentEvents(
      TulevaFund fund, TrackingCheckType checkType, LocalDate checkDate, int limit);
}
