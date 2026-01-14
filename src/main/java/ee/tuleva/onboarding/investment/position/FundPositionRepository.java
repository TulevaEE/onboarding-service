package ee.tuleva.onboarding.investment.position;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FundPositionRepository extends JpaRepository<FundPosition, Long> {

  boolean existsByReportingDateAndFundCodeAndAccountName(
      LocalDate reportingDate, String fundCode, String accountName);

  List<FundPosition> findByReportingDateAndFundCodeAndAccountType(
      LocalDate reportingDate, String fundCode, AccountType accountType);
}
