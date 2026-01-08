package ee.tuleva.onboarding.investment.portfolio;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FundLimitRepository extends JpaRepository<FundLimit, Long> {

  Optional<FundLimit> findByFundCodeAndEffectiveDate(String fundCode, LocalDate effectiveDate);

  @Query(
      """
      SELECT f FROM FundLimit f
      WHERE f.fundCode = :fundCode
      AND f.effectiveDate = (
        SELECT MAX(f2.effectiveDate)
        FROM FundLimit f2
        WHERE f2.fundCode = :fundCode
      )
      """)
  Optional<FundLimit> findLatestByFundCode(String fundCode);
}
