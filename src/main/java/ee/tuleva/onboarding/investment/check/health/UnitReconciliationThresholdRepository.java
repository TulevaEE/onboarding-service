package ee.tuleva.onboarding.investment.check.health;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface UnitReconciliationThresholdRepository
    extends JpaRepository<UnitReconciliationThreshold, Long> {

  Optional<UnitReconciliationThreshold> findByFundCode(TulevaFund fundCode);
}
