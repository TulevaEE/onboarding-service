package ee.tuleva.onboarding.investment.portfolio;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ModelPortfolioAllocationRepository
    extends JpaRepository<ModelPortfolioAllocation, Long> {

  List<ModelPortfolioAllocation> findByFundAndEffectiveDate(
      TulevaFund fund, LocalDate effectiveDate);

  Optional<ModelPortfolioAllocation>
      findFirstByIsinAndProviderIsNotNullAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
          String isin, LocalDate effectiveDate);

  // Snapshot semantics: a model portfolio is versioned as a whole, so all allocations on a given
  // effective date form one indivisible version. Returns every row from the single newest
  // effectiveDate <= :asOf. Contrast with PositionLimit/ProviderLimit, where each ISIN/provider
  // versions independently.
  @Query(
      """
      SELECT m FROM ModelPortfolioAllocation m
      WHERE m.fund = :fund
      AND m.effectiveDate = (
        SELECT MAX(m2.effectiveDate)
        FROM ModelPortfolioAllocation m2
        WHERE m2.fund = :fund AND m2.effectiveDate <= :asOf
      )
      """)
  List<ModelPortfolioAllocation> findLatestByFundAsOf(TulevaFund fund, LocalDate asOf);

  @Query(
      """
      SELECT m FROM ModelPortfolioAllocation m
      WHERE m.fund = :fund
      AND m.effectiveDate = (
        SELECT MAX(m2.effectiveDate)
        FROM ModelPortfolioAllocation m2
        WHERE m2.fund = :fund AND m2.effectiveDate <= :asOf
        AND m2.effectiveDate < (
          SELECT MAX(m3.effectiveDate)
          FROM ModelPortfolioAllocation m3
          WHERE m3.fund = :fund AND m3.effectiveDate <= :asOf
        )
      )
      """)
  List<ModelPortfolioAllocation> findPreviousByFundAsOf(TulevaFund fund, LocalDate asOf);

  @Query(
      """
      SELECT a FROM ModelPortfolioAllocation a
      WHERE a.fund = :fund
        AND a.effectiveDate IN (
          SELECT DISTINCT mpa.effectiveDate FROM ModelPortfolioAllocation mpa
          WHERE mpa.fund = :fund AND mpa.effectiveDate <= :end
            AND mpa.effectiveDate >= (
              SELECT MAX(mpa2.effectiveDate) FROM ModelPortfolioAllocation mpa2
              WHERE mpa2.fund = :fund AND mpa2.effectiveDate <= :start
            )
        )
      ORDER BY a.effectiveDate, a.isin
      """)
  List<ModelPortfolioAllocation> findVersionsActiveDuringPeriod(
      TulevaFund fund, LocalDate start, LocalDate end);
}
