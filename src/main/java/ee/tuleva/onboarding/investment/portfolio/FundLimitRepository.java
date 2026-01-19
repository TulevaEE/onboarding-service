package ee.tuleva.onboarding.investment.portfolio;

import ee.tuleva.onboarding.investment.TulevaFund;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FundLimitRepository extends JpaRepository<FundLimit, Long> {

  Optional<FundLimit> findByFundAndEffectiveDate(TulevaFund fund, LocalDate effectiveDate);

  @Query(
      """
      SELECT f FROM FundLimit f
      WHERE f.fund = :fund
      AND f.effectiveDate = (
        SELECT MAX(f2.effectiveDate)
        FROM FundLimit f2
        WHERE f2.fund = :fund
      )
      """)
  Optional<FundLimit> findLatestByFund(TulevaFund fund);
}
