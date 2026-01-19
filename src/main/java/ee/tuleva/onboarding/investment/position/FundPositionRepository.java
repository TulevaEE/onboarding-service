package ee.tuleva.onboarding.investment.position;

import ee.tuleva.onboarding.investment.TulevaFund;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FundPositionRepository extends JpaRepository<FundPosition, Long> {

  boolean existsByReportingDateAndFundAndAccountName(
      LocalDate reportingDate, TulevaFund fund, String accountName);

  List<FundPosition> findByReportingDateAndFundAndAccountType(
      LocalDate reportingDate, TulevaFund fund, AccountType accountType);

  @Query("SELECT MAX(fp.reportingDate) FROM FundPosition fp WHERE fp.fund = :fund")
  Optional<LocalDate> findLatestReportingDateByFund(TulevaFund fund);
}
