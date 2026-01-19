package ee.tuleva.onboarding.investment.portfolio;

import ee.tuleva.onboarding.investment.TulevaFund;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProviderLimitRepository extends JpaRepository<ProviderLimit, Long> {

  List<ProviderLimit> findByFundAndEffectiveDate(TulevaFund fund, LocalDate effectiveDate);

  Optional<ProviderLimit> findByFundAndEffectiveDateAndProvider(
      TulevaFund fund, LocalDate effectiveDate, Provider provider);

  @Query(
      """
      SELECT pl FROM ProviderLimit pl
      WHERE pl.fund = :fund
      AND pl.effectiveDate = (
        SELECT MAX(pl2.effectiveDate)
        FROM ProviderLimit pl2
        WHERE pl2.fund = :fund
      )
      """)
  List<ProviderLimit> findLatestByFund(TulevaFund fund);
}
