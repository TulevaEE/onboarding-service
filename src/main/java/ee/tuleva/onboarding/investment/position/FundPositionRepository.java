package ee.tuleva.onboarding.investment.position;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
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

  @Query(
      """
      SELECT fp.marketValue FROM FundPosition fp
      WHERE fp.fund = :fund AND fp.accountId = :accountId AND fp.reportingDate <= :asOfDate
      ORDER BY fp.reportingDate DESC
      LIMIT 1
      """)
  Optional<BigDecimal> findMarketValueByFundAndAccountId(
      TulevaFund fund, String accountId, LocalDate asOfDate);

  @Query(
      """
      SELECT COALESCE(SUM(fp.marketValue), 0) FROM FundPosition fp
      WHERE fp.fund = :fund AND fp.reportingDate = (
          SELECT MAX(fp2.reportingDate) FROM FundPosition fp2
          WHERE fp2.fund = :fund AND fp2.reportingDate <= :asOfDate
      )
      """)
  BigDecimal sumMarketValueByFund(TulevaFund fund, LocalDate asOfDate);

  @Query(
      """
      SELECT MAX(fp.reportingDate) FROM FundPosition fp
      WHERE fp.fund = :fund AND fp.reportingDate <= :asOfDate
      """)
  Optional<LocalDate> findLatestReportingDateByFundAndAsOfDate(TulevaFund fund, LocalDate asOfDate);
}
