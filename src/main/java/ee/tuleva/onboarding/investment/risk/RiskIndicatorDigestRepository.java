package ee.tuleva.onboarding.investment.risk;

import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

interface RiskIndicatorDigestRepository extends JpaRepository<RiskIndicatorDigest, Long> {

  boolean existsByDigestMonth(LocalDate digestMonth);
}
