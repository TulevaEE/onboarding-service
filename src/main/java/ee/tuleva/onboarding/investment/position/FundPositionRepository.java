package ee.tuleva.onboarding.investment.position;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FundPositionRepository extends JpaRepository<FundPosition, Long> {

  boolean existsByReportingDateAndFundCodeAndAccountName(
      LocalDate reportingDate, String fundCode, String accountName);

  List<FundPosition> findByReportingDateAndFundCodeAndAccountType(
      LocalDate reportingDate, String fundCode, AccountType accountType);

  @Query("SELECT MAX(fp.reportingDate) FROM FundPosition fp WHERE fp.fundCode = :fundCode")
  Optional<LocalDate> findLatestReportingDateByFundCode(String fundCode);
}
