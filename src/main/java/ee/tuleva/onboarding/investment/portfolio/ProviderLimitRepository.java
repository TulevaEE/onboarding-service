package ee.tuleva.onboarding.investment.portfolio;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProviderLimitRepository extends JpaRepository<ProviderLimit, Long> {

  List<ProviderLimit> findByFundCodeAndEffectiveDate(String fundCode, LocalDate effectiveDate);

  Optional<ProviderLimit> findByFundCodeAndEffectiveDateAndProvider(
      String fundCode, LocalDate effectiveDate, Provider provider);

  @Query(
      """
      SELECT pl FROM ProviderLimit pl
      WHERE pl.fundCode = :fundCode
      AND pl.effectiveDate = (
        SELECT MAX(pl2.effectiveDate)
        FROM ProviderLimit pl2
        WHERE pl2.fundCode = :fundCode
      )
      """)
  List<ProviderLimit> findLatestByFundCode(String fundCode);
}
