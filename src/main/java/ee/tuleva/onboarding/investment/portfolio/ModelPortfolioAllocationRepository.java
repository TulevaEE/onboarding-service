package ee.tuleva.onboarding.investment.portfolio;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ModelPortfolioAllocationRepository
    extends JpaRepository<ModelPortfolioAllocation, Long> {

  List<ModelPortfolioAllocation> findByFundCodeAndEffectiveDate(
      String fundCode, LocalDate effectiveDate);

  @Query(
      """
      SELECT m FROM ModelPortfolioAllocation m
      WHERE m.fundCode = :fundCode
      AND m.effectiveDate = (
        SELECT MAX(m2.effectiveDate)
        FROM ModelPortfolioAllocation m2
        WHERE m2.fundCode = :fundCode
      )
      """)
  List<ModelPortfolioAllocation> findLatestByFundCode(String fundCode);
}
