package ee.tuleva.onboarding.investment.check.tracking;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

interface PeriodicTdAttributionRepository extends JpaRepository<PeriodicTdAttribution, Long> {

  @Transactional
  @Modifying
  void deleteByFundAndPeriodStartAndPeriodEndAndPeriodType(
      TulevaFund fund, LocalDate periodStart, LocalDate periodEnd, PeriodType periodType);
}
