package ee.tuleva.onboarding.administration;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PortfolioAnalyticsRepository extends JpaRepository<PortfolioAnalytics, Long> {
  Optional<PortfolioAnalytics> findByDate(LocalDate date);
}
