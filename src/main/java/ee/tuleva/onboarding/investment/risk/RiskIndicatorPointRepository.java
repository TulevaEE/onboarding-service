package ee.tuleva.onboarding.investment.risk;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface RiskIndicatorPointRepository extends JpaRepository<RiskIndicatorPoint, Long> {

  List<RiskIndicatorPoint> findByIndicatorTypeAndFundOrderByAsOfDateAsc(
      RiskIndicatorType indicatorType, TulevaFund fund);

  List<RiskIndicatorPoint> findByIndicatorTypeAndFundAndAsOfDateBetweenOrderByAsOfDateAsc(
      RiskIndicatorType indicatorType, TulevaFund fund, LocalDate from, LocalDate to);
}
