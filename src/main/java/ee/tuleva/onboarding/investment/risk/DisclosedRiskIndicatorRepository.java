package ee.tuleva.onboarding.investment.risk;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface DisclosedRiskIndicatorRepository extends JpaRepository<DisclosedRiskIndicator, Long> {

  Optional<DisclosedRiskIndicator>
      findFirstByIndicatorTypeAndFundAndDisclosedFromLessThanEqualOrderByDisclosedFromDesc(
          RiskIndicatorType indicatorType, TulevaFund fund, LocalDate asOfDate);
}
