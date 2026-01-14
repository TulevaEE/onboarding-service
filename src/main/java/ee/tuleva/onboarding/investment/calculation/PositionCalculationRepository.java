package ee.tuleva.onboarding.investment.calculation;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PositionCalculationRepository
    extends JpaRepository<InvestmentPositionCalculation, Long> {

  Optional<InvestmentPositionCalculation> findByIsinAndFundAndDate(
      String isin, TulevaFund fund, LocalDate date);
}
