package ee.tuleva.onboarding.investment.fees;

import ee.tuleva.onboarding.investment.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class FeeAccrualRepository {

  private final JdbcClient jdbcClient;

  public BigDecimal getAccruedFeesForMonth(
      TulevaFund fund, LocalDate feeMonth, List<FeeType> feeTypes, LocalDate beforeDate) {
    return jdbcClient
        .sql(
            """
            SELECT COALESCE(SUM(daily_amount_gross), 0)
            FROM investment_fee_accrual
            WHERE fund_code = :fundCode
              AND fee_month = :feeMonth
              AND fee_type IN (:feeTypes)
              AND accrual_date < :beforeDate
            """)
        .param("fundCode", fund.name())
        .param("feeMonth", feeMonth)
        .param("feeTypes", feeTypes.stream().map(FeeType::name).toList())
        .param("beforeDate", beforeDate)
        .query(BigDecimal.class)
        .single();
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
                    daily_amount_net = :dailyAmountNet,
                    daily_amount_gross = :dailyAmountGross,
                    vat_rate = :vatRate,
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
            .param("dailyAmountNet", accrual.dailyAmountNet())
            .param("dailyAmountGross", accrual.dailyAmountGross())
            .param("vatRate", accrual.vatRate())
            .param("daysInYear", accrual.daysInYear())
            .param("referenceDate", accrual.referenceDate())
            .update();

    if (updated == 0) {
      jdbcClient
          .sql(
              """
              INSERT INTO investment_fee_accrual (
                  fund_code, fee_type, accrual_date, fee_month, base_value,
                  annual_rate, daily_amount_net, daily_amount_gross, vat_rate,
                  days_in_year, reference_date
              )
              VALUES (
                  :fundCode, :feeType, :accrualDate, :feeMonth, :baseValue,
                  :annualRate, :dailyAmountNet, :dailyAmountGross, :vatRate,
                  :daysInYear, :referenceDate
              )
              """)
          .param("fundCode", accrual.fund().name())
          .param("feeType", accrual.feeType().name())
          .param("accrualDate", accrual.accrualDate())
          .param("feeMonth", accrual.feeMonth())
          .param("baseValue", accrual.baseValue())
          .param("annualRate", accrual.annualRate())
          .param("dailyAmountNet", accrual.dailyAmountNet())
          .param("dailyAmountGross", accrual.dailyAmountGross())
          .param("vatRate", accrual.vatRate())
          .param("daysInYear", accrual.daysInYear())
          .param("referenceDate", accrual.referenceDate())
          .update();
    }
  }
}
