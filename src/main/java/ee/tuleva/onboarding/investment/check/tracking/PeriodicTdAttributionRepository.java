package ee.tuleva.onboarding.investment.check.tracking;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface PeriodicTdAttributionRepository extends JpaRepository<PeriodicTdAttribution, Long> {

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
