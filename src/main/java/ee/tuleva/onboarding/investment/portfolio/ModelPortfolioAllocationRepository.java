package ee.tuleva.onboarding.investment.portfolio;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ModelPortfolioAllocationRepository
    extends JpaRepository<ModelPortfolioAllocation, Long> {

  List<ModelPortfolioAllocation> findByFundAndEffectiveDate(
      TulevaFund fund, LocalDate effectiveDate);

  @Query(
      """
      SELECT m FROM ModelPortfolioAllocation m
      WHERE m.fund = :fund
      AND m.effectiveDate = (
        SELECT MAX(m2.effectiveDate)
        FROM ModelPortfolioAllocation m2
        WHERE m2.fund = :fund
      )
      """)
  List<ModelPortfolioAllocation> findLatestByFund(TulevaFund fund);
}
