package ee.tuleva.onboarding.investment.check.health;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface HealthCheckEventRepository extends JpaRepository<HealthCheckEvent, Long> {

  List<HealthCheckEvent> findByFundAndCheckDate(TulevaFund fund, LocalDate checkDate);

  List<HealthCheckEvent> findTop2ByFundAndCheckDateAndCheckTypeOrderByCreatedAtDesc(
      TulevaFund fund, LocalDate checkDate, HealthCheckType checkType);
}
