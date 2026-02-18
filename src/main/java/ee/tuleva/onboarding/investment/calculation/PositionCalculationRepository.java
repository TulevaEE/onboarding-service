package ee.tuleva.onboarding.investment.calculation;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PositionCalculationRepository
    extends JpaRepository<InvestmentPositionCalculation, Long> {

  Optional<InvestmentPositionCalculation> findByIsinAndFundAndDate(
      String isin, TulevaFund fund, LocalDate date);

  List<InvestmentPositionCalculation> findByFundAndDate(TulevaFund fund, LocalDate date);

  @Query(
      "SELECT SUM(p.calculatedMarketValue) FROM InvestmentPositionCalculation p "
          + "WHERE p.fund = :fund AND p.date = :date")
  Optional<BigDecimal> getTotalMarketValue(
      @Param("fund") TulevaFund fund, @Param("date") LocalDate date);

  @Query(
      "SELECT MAX(p.date) FROM InvestmentPositionCalculation p "
          + "WHERE p.fund = :fund AND p.date <= :date")
  Optional<LocalDate> getLatestDateUpTo(
      @Param("fund") TulevaFund fund, @Param("date") LocalDate date);

  @Query("SELECT MAX(p.date) FROM InvestmentPositionCalculation p " + "WHERE p.date <= :date")
  Optional<LocalDate> getLatestDateUpTo(@Param("date") LocalDate date);

  @Query(
      "SELECT SUM(p.calculatedMarketValue) FROM InvestmentPositionCalculation p "
          + "WHERE p.date = :date")
  Optional<BigDecimal> getTotalMarketValueAllFunds(@Param("date") LocalDate date);
}
