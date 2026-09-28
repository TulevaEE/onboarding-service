package ee.tuleva.onboarding.investment.cashbuffer;

import static ee.tuleva.onboarding.investment.fees.FeeType.DEPOT;
import static ee.tuleva.onboarding.investment.fees.FeeType.MANAGEMENT;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.fees.FeeAccrual;
import ee.tuleva.onboarding.investment.fees.FeeAccrualRepository;
import ee.tuleva.onboarding.investment.fees.FeeChargedToFundPolicy;
import ee.tuleva.onboarding.investment.fees.FeeType;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
@Import({FeeAccrualRepository.class, FeeChargedToFundPolicy.class, ChargedFeeAccruals.class})
class ChargedFeeAccrualsTest {

  private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);

  @Autowired private FeeAccrualRepository feeAccrualRepository;
  @Autowired private JdbcClient jdbcClient;
  @Autowired private ChargedFeeAccruals chargedFeeAccruals;

  @Test
  void addsUpTheMonthsAccrualsOfEveryFeeTheFundItselfPays() {
    daily(MANAGEMENT, SEPTEMBER, "100.004");
    daily(DEPOT, SEPTEMBER, "10.00");
    daily(MANAGEMENT, SEPTEMBER.plusMonths(1), "999.00");

    assertThat(chargedFeeAccruals.accruedDuring(TUK75, SEPTEMBER))
        .hasValueSatisfying(total -> assertThat(total).isEqualByComparingTo("3000.12"));
  }

  @Test
  void countsTheDepotFeeFromTheDayThePolicyChargesItToTheFund() {
    jdbcClient
        .sql(
            """
            UPDATE investment_fee_policy SET valid_to = DATE '2026-09-20'
            WHERE fund_code = 'TUK75' AND fee_type = 'DEPOT'
            """)
        .update();
    jdbcClient
        .sql(
            """
            INSERT INTO investment_fee_policy (fund_code, fee_type, charged_to_fund, valid_from)
            VALUES ('TUK75', 'DEPOT', true, DATE '2026-09-21')
            """)
        .update();
    daily(MANAGEMENT, SEPTEMBER, "100.00");
    daily(DEPOT, SEPTEMBER, "10.00");

    assertThat(chargedFeeAccruals.accruedDuring(TUK75, SEPTEMBER))
        .hasValueSatisfying(total -> assertThat(total).isEqualByComparingTo("3100.00"));
  }

  @Test
  void aMonthTheFeeCalculationNeverAccruedIsMissingRatherThanZero() {
    daily(MANAGEMENT, SEPTEMBER.minusMonths(1), "100.00");

    assertThat(chargedFeeAccruals.accruedDuring(TUK75, SEPTEMBER)).isEmpty();
  }

  private void daily(FeeType feeType, YearMonth month, String amount) {
    Stream.iterate(
            month.atDay(1), day -> !day.isAfter(month.atEndOfMonth()), day -> day.plusDays(1))
        .forEach(
            day ->
                feeAccrualRepository.save(
                    FeeAccrual.builder()
                        .fund(TUK75)
                        .feeType(feeType)
                        .accrualDate(day)
                        .feeMonth(month.atDay(1))
                        .baseValue(new BigDecimal("100000000"))
                        .annualRate(new BigDecimal("0.00365"))
                        .dailyAmountGross(new BigDecimal(amount))
                        .daysInYear(365)
                        .build()));
  }
}
