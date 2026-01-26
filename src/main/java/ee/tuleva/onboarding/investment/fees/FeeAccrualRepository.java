package ee.tuleva.onboarding.investment.fees;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class FeeAccrualRepository {

  private final JdbcClient jdbcClient;

  public void save(FeeAccrual accrual) {
    int updated =
        jdbcClient
            .sql(
                """
                UPDATE fee_accrual SET
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
              INSERT INTO fee_accrual (
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
