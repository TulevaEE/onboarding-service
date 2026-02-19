package ee.tuleva.onboarding.investment.position;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FundPositionRepository extends JpaRepository<FundPosition, Long> {

  boolean existsByNavDateAndFundAndAccountName(
      LocalDate navDate, TulevaFund fund, String accountName);

  FundPosition findByNavDateAndFundAndAccountName(
      LocalDate navDate, TulevaFund fund, String accountName);

  List<FundPosition> findByNavDateAndFundAndAccountType(
      LocalDate navDate, TulevaFund fund, AccountType accountType);

  @Query("SELECT MAX(fp.navDate) FROM FundPosition fp WHERE fp.fund = :fund")
  Optional<LocalDate> findLatestNavDateByFund(TulevaFund fund);

  @Query(
      "SELECT DISTINCT fp.navDate FROM FundPosition fp WHERE fp.fund = :fund ORDER BY fp.navDate")
  List<LocalDate> findDistinctNavDatesByFund(TulevaFund fund);

  @Query(
      """
      SELECT fp.marketValue FROM FundPosition fp
      WHERE fp.fund = :fund AND fp.accountId = :accountId AND fp.navDate <= :asOfDate
      ORDER BY fp.navDate DESC
      LIMIT 1
      """)
  Optional<BigDecimal> findMarketValueByFundAndAccountId(
      TulevaFund fund, String accountId, LocalDate asOfDate);

  @Query(
      """
      SELECT COALESCE(SUM(fp.marketValue), 0) FROM FundPosition fp
      WHERE fp.fund = :fund AND fp.navDate = (
          SELECT MAX(fp2.navDate) FROM FundPosition fp2
          WHERE fp2.fund = :fund AND fp2.navDate <= :asOfDate
      )
      """)
  BigDecimal sumMarketValueByFund(TulevaFund fund, LocalDate asOfDate);

  @Query(
      """
      SELECT MAX(fp.navDate) FROM FundPosition fp
      WHERE fp.fund = :fund AND fp.navDate <= :asOfDate
      """)
  Optional<LocalDate> findLatestNavDateByFundAndAsOfDate(TulevaFund fund, LocalDate asOfDate);

  @Query(
      """
      SELECT COALESCE(SUM(fp.marketValue), 0) FROM FundPosition fp
      WHERE fp.fund = :fund
      AND fp.navDate = :navDate
      AND fp.accountType IN :accountTypes
      """)
  BigDecimal sumMarketValueByFundAndAccountTypes(
      TulevaFund fund, LocalDate navDate, List<AccountType> accountTypes);
}
