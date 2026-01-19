package ee.tuleva.onboarding.investment.portfolio;

import ee.tuleva.onboarding.investment.TulevaFund;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PositionLimitRepository extends JpaRepository<PositionLimit, Long> {

  List<PositionLimit> findByFundAndEffectiveDate(TulevaFund fund, LocalDate effectiveDate);

  @Query(
      """
      SELECT p FROM PositionLimit p
      WHERE p.fund = :fund
      AND p.effectiveDate = (
        SELECT MAX(p2.effectiveDate)
        FROM PositionLimit p2
        WHERE p2.fund = :fund
      )
      """)
  List<PositionLimit> findLatestByFund(TulevaFund fund);
}
