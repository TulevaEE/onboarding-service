package ee.tuleva.onboarding.investment.position;

import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FundPositionRepository extends JpaRepository<FundPosition, Long> {

  boolean existsByReportingDate(LocalDate reportingDate);

  boolean existsByReportingDateAndFundCodeAndAccountName(
      LocalDate reportingDate, String fundCode, String accountName);
}
