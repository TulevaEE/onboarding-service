package ee.tuleva.onboarding.investment.portfolio;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PositionLimitRepository extends JpaRepository<PositionLimit, Long> {

  List<PositionLimit> findByFundAndEffectiveDate(TulevaFund fund, LocalDate effectiveDate);

  // Per-ISIN latest version as of :asOf. Limits can be introduced incrementally for individual
  // ISINs, so we resolve the newest effectiveDate independently for each isin (and for the
  // NULL-isin index-group aggregate row, which uses IS NULL matching).
  @Query(
      """
      SELECT p FROM PositionLimit p
      WHERE p.fund = :fund
      AND p.effectiveDate = (
        SELECT MAX(p2.effectiveDate)
        FROM PositionLimit p2
        WHERE p2.fund = :fund
        AND (p2.isin = p.isin OR (p2.isin IS NULL AND p.isin IS NULL))
        AND p2.effectiveDate <= :asOf
      )
      """)
  List<PositionLimit> findLatestByFundAsOf(TulevaFund fund, LocalDate asOf);
}
