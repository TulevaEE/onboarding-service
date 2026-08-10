package ee.tuleva.onboarding.investment.risk;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface RiskIndicatorPublicationRepository extends JpaRepository<RiskIndicatorPublication, Long> {

  Optional<RiskIndicatorPublication>
      findFirstByIndicatorTypeAndFundAndNotifiedTrueOrderByEvaluationDateDesc(
          RiskIndicatorType indicatorType, TulevaFund fund);

  Optional<RiskIndicatorPublication> findByIndicatorTypeAndFundAndEvaluationDate(
      RiskIndicatorType indicatorType, TulevaFund fund, LocalDate evaluationDate);
}
