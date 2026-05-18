package ee.tuleva.onboarding.investment.transaction.portfolio;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioBaselineRepository extends JpaRepository<PortfolioBaseline, Long> {

  Optional<PortfolioBaseline> findByFundIsin(String fundIsin);
}
