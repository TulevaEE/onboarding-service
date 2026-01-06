package ee.tuleva.onboarding.investment.position;

import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FundPositionRepository extends JpaRepository<FundPosition, Long> {

  boolean existsByNavDate(LocalDate navDate);

  boolean existsByNavDateAndFundCodeAndAssetName(
      LocalDate navDate, String fundCode, String assetName);
}
