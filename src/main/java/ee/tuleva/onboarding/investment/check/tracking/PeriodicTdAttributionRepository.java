package ee.tuleva.onboarding.investment.check.tracking;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

interface PeriodicTdAttributionRepository extends JpaRepository<PeriodicTdAttribution, Long> {

  @Modifying
  void deleteByFundAndPeriodStartAndPeriodEndAndPeriodType(
      TulevaFund fund, LocalDate periodStart, LocalDate periodEnd, PeriodType periodType);
}
