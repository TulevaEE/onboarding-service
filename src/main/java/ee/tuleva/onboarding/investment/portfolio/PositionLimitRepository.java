package ee.tuleva.onboarding.investment.portfolio;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PositionLimitRepository extends JpaRepository<PositionLimit, Long> {

  List<PositionLimit> findByFundCodeAndEffectiveDate(String fundCode, LocalDate effectiveDate);

  @Query(
      """
      SELECT p FROM PositionLimit p
      WHERE p.fundCode = :fundCode
      AND p.effectiveDate = (
        SELECT MAX(p2.effectiveDate)
        FROM PositionLimit p2
        WHERE p2.fundCode = :fundCode
      )
      """)
  List<PositionLimit> findLatestByFundCode(String fundCode);
}
