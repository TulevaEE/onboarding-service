package ee.tuleva.onboarding.investment.check.tracking;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface PeriodicTdAttributionRepository extends JpaRepository<PeriodicTdAttribution, Long> {

  // A derived delete only queues each removal until flush, while an IDENTITY insert runs at once,
  // so replacing a stored period in one transaction hit uq_td_attribution. The detail rows go
  // with it through the foreign key's ON DELETE CASCADE.
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      DELETE FROM PeriodicTdAttribution a
      WHERE a.fund = :fund
        AND a.periodStart = :periodStart
        AND a.periodEnd = :periodEnd
        AND a.periodType = :periodType
      """)
  void deleteByFundAndPeriodStartAndPeriodEndAndPeriodType(
      TulevaFund fund, LocalDate periodStart, LocalDate periodEnd, PeriodType periodType);
}
