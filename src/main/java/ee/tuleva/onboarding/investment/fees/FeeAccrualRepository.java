package ee.tuleva.onboarding.investment.fees;

import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class FeeAccrualRepository {

  private final JdbcClient jdbcClient;

  public Map<LocalDate, BigDecimal> getAccruedFeesByDateForMonth(
      TulevaFund fund, LocalDate feeMonth, List<FeeType> feeTypes, LocalDate beforeDate) {
    return jdbcClient
        .sql(
            """
            SELECT accrual_date, COALESCE(SUM(daily_amount_gross), 0) AS amount
            FROM investment_fee_accrual
            WHERE fund_code = :fundCode
              AND fee_month = :feeMonth
              AND fee_type IN (:feeTypes)
              AND accrual_date < :beforeDate
            GROUP BY accrual_date
            """)
        .param("fundCode", fund.name())
        .param("feeMonth", feeMonth)
        .param("feeTypes", feeTypes.stream().map(FeeType::name).toList())
        .param("beforeDate", beforeDate)
        .query((rs, rowNum) -> Map.entry(rs.getDate(1).toLocalDate(), rs.getBigDecimal(2)))
        .list()
        .stream()
        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  public Map<LocalDate, BigDecimal> getUnsettledAccrualByDate(
      TulevaFund fund, FeeType feeType, LocalDate asOfDate) {
    return jdbcClient
        .sql(
            """
            SELECT accrual_date, COALESCE(SUM(daily_amount_gross), 0) AS amount
            FROM investment_fee_accrual
            WHERE fund_code = :fundCode
              AND fee_type = :feeType
              AND fee_month = :feeMonth
              AND accrual_date <= :asOfDate
            GROUP BY accrual_date
            """)
        .param("fundCode", fund.name())
        .param("feeType", feeType.name())
        .param("feeMonth", asOfDate.withDayOfMonth(1))
        .param("asOfDate", asOfDate)
        .query((rs, rowNum) -> Map.entry(rs.getDate(1).toLocalDate(), rs.getBigDecimal(2)))
        .list()
        .stream()
        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  public boolean existsByFundAndFeeMonth(TulevaFund fund, LocalDate feeMonth) {
    return jdbcClient
            .sql(
                """
            SELECT COUNT(*) FROM investment_fee_accrual
            WHERE fund_code = :fundCode AND fee_month = :feeMonth
            """)
            .param("fundCode", fund.name())
            .param("feeMonth", feeMonth)
            .query(Integer.class)
            .single()
        > 0;
  }

  public List<DailyAccrualAmount> findRoundedDailyGrossBetween(
      TulevaFund fund, FeeType feeType, LocalDate from, LocalDate to) {
    return jdbcClient
        .sql(
            """
            SELECT accrual_date, ROUND(daily_amount_gross, 2) AS rounded_gross
            FROM investment_fee_accrual
            WHERE fund_code = :fundCode AND fee_type = :feeType
              AND accrual_date BETWEEN :from AND :to
            ORDER BY accrual_date
            """)
        .param("fundCode", fund.name())
        .param("feeType", feeType.name())
        .param("from", from)
        .param("to", to)
        .query(
            (rs, rowNum) ->
                new DailyAccrualAmount(
                    rs.getDate("accrual_date").toLocalDate(), rs.getBigDecimal("rounded_gross")))
        .list();
  }

  public List<FeeBaseValue> findBaseValuesBetween(TulevaFund fund, LocalDate from, LocalDate to) {
    return jdbcClient
        .sql(
            """
            SELECT accrual_date, fee_type, base_value
            FROM investment_fee_accrual
            WHERE fund_code = :fundCode AND accrual_date BETWEEN :from AND :to
            ORDER BY accrual_date, fee_type
            """)
        .param("fundCode", fund.name())
        .param("from", from)
        .param("to", to)
        .query(
            (rs, rowNum) ->
                new FeeBaseValue(
                    rs.getDate("accrual_date").toLocalDate(),
                    FeeType.valueOf(rs.getString("fee_type")),
                    rs.getBigDecimal("base_value")))
        .list();
  }

  public Optional<FeeAccrual> findByFundAndAccrualDateAndFeeType(
      TulevaFund fund, LocalDate accrualDate, FeeType feeType) {
    return jdbcClient
        .sql(
            """
            SELECT * FROM investment_fee_accrual
            WHERE fund_code = :fundCode
              AND accrual_date = :accrualDate
              AND fee_type = :feeType
            """)
        .param("fundCode", fund.name())
        .param("accrualDate", accrualDate)
        .param("feeType", feeType.name())
        .query(FeeAccrual::fromResultSet)
        .optional();
  }

  public Optional<BigDecimal> findLatestBaseValue(TulevaFund fund, FeeType feeType) {
    return jdbcClient
        .sql(
            """
            SELECT base_value FROM investment_fee_accrual
            WHERE fund_code = :fundCode AND fee_type = :feeType
            ORDER BY accrual_date DESC
            LIMIT 1
            """)
        .param("fundCode", fund.name())
        .param("feeType", feeType.name())
        .query(BigDecimal.class)
        .optional();
  }

  public Optional<LocalDate> findLatestAccrualDate(TulevaFund fund) {
    return jdbcClient
        .sql(
            """
            SELECT accrual_date FROM investment_fee_accrual
            WHERE fund_code = :fundCode
            ORDER BY accrual_date DESC
            LIMIT 1
            """)
        .param("fundCode", fund.name())
        .query(LocalDate.class)
        .optional();
  }

  public int deleteByFund(TulevaFund fund) {
    return jdbcClient
        .sql("DELETE FROM investment_fee_accrual WHERE fund_code = :fundCode")
        .param("fundCode", fund.name())
        .update();
  }

  public List<FeeAccrual> findByFundAndDateRange(TulevaFund fund, LocalDate start, LocalDate end) {
    return jdbcClient
        .sql(
            """
            SELECT * FROM investment_fee_accrual
            WHERE fund_code = :fundCode
              AND accrual_date BETWEEN :start AND :end
            ORDER BY accrual_date, fee_type
            """)
        .param("fundCode", fund.name())
        .param("start", start)
        .param("end", end)
        .query(FeeAccrual::fromResultSet)
        .list();
  }

  public int deleteByFundFromDate(TulevaFund fund, LocalDate fromDate) {
    return jdbcClient
        .sql(
            "DELETE FROM investment_fee_accrual WHERE fund_code = :fundCode AND accrual_date >= :fromDate")
        .param("fundCode", fund.name())
        .param("fromDate", fromDate)
        .update();
  }

  public void save(FeeAccrual accrual) {
    int updated =
        jdbcClient
            .sql(
                """
                UPDATE investment_fee_accrual SET
                    fee_month = :feeMonth,
                    base_value = :baseValue,
                    annual_rate = :annualRate,
                    daily_amount_net = :dailyAmountGross,
                    daily_amount_gross = :dailyAmountGross,
                    days_in_year = :daysInYear,
                    reference_date = :referenceDate
                WHERE fund_code = :fundCode
                  AND fee_type = :feeType
                  AND accrual_date = :accrualDate
                """)
            .param("fundCode", accrual.fund().name())
            .param("feeType", accrual.feeType().name())
            .param("accrualDate", accrual.accrualDate())
            .param("feeMonth", accrual.feeMonth())
            .param("baseValue", accrual.baseValue())
            .param("annualRate", accrual.annualRate())
            .param("dailyAmountGross", accrual.dailyAmountGross())
            .param("daysInYear", accrual.daysInYear())
            .param("referenceDate", accrual.referenceDate())
            .update();

    if (updated == 0) {
      jdbcClient
          .sql(
              """
              INSERT INTO investment_fee_accrual (
                  fund_code, fee_type, accrual_date, fee_month, base_value,
                  annual_rate, daily_amount_net, daily_amount_gross,
                  days_in_year, reference_date
              )
              VALUES (
                  :fundCode, :feeType, :accrualDate, :feeMonth, :baseValue,
                  :annualRate, :dailyAmountGross, :dailyAmountGross,
                  :daysInYear, :referenceDate
              )
              """)
          .param("fundCode", accrual.fund().name())
          .param("feeType", accrual.feeType().name())
          .param("accrualDate", accrual.accrualDate())
          .param("feeMonth", accrual.feeMonth())
          .param("baseValue", accrual.baseValue())
          .param("annualRate", accrual.annualRate())
          .param("dailyAmountGross", accrual.dailyAmountGross())
          .param("daysInYear", accrual.daysInYear())
          .param("referenceDate", accrual.referenceDate())
          .update();
    }
  }
}
