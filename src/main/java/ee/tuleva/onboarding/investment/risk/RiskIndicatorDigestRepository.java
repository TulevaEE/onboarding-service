package ee.tuleva.onboarding.investment.risk;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface RiskIndicatorDigestRepository extends JpaRepository<RiskIndicatorDigest, Long> {

  Optional<RiskIndicatorDigest> findByDigestMonth(LocalDate digestMonth);
}
