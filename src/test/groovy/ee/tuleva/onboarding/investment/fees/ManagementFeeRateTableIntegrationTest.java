package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
@Import({
  FeeRateRepository.class,
  DepotFeeTierRepository.class,
  FeeAccrualRepository.class,
  FeeChargedToFundPolicy.class,
  ManagementFeeRateTable.class
})
class ManagementFeeRateTableIntegrationTest {

  @Autowired private JdbcClient jdbcClient;
  @Autowired private ManagementFeeRateTable managementFeeRateTable;

  @BeforeEach
  void setUp() {
    jdbcClient.sql("DELETE FROM investment_fee_rate").update();
  }

  @Test
  void theNewRateIsInForceFromItsFirstDayAndTheOldOneUntilItsLast() {
    insertRate(TKF100, "MANAGEMENT", "0.00152", "2026-02-27", "2026-10-28");
    insertRate(TKF100, "MANAGEMENT", "0.00178", "2026-10-29", null);

    assertThat(managementFeeRateTable.rateInForceOn(TKF100, LocalDate.parse("2026-10-28")))
        .hasValueSatisfying(rate -> assertThat(rate).isEqualByComparingTo("0.00152"));
    assertThat(managementFeeRateTable.rateInForceOn(TKF100, LocalDate.parse("2026-10-29")))
        .hasValueSatisfying(rate -> assertThat(rate).isEqualByComparingTo("0.00178"));
  }

  @Test
  void anotherFeeTypeOrAnotherFundIsNotAManagementFeeRate() {
    insertRate(TKF100, "DEPOT", "0.00035", "2026-02-01", null);
    insertRate(TUK75, "MANAGEMENT", "0.00205", "2026-02-27", null);

    assertThat(managementFeeRateTable.rateInForceOn(TKF100, LocalDate.parse("2026-10-06")))
        .isEmpty();
  }

  @Test
  void noRateIsInForceBeforeTheFirstOneStarts() {
    insertRate(TKF100, "MANAGEMENT", "0.0016", "2026-02-01", null);

    assertThat(managementFeeRateTable.rateInForceOn(TKF100, LocalDate.parse("2026-01-31")))
        .isEmpty();
  }

  private void insertRate(
      TulevaFund fund, String feeType, String rate, String validFrom, @Nullable String validTo) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_fee_rate (fund_code, fee_type, annual_rate, valid_from, valid_to)
            VALUES (:fundCode, :feeType, :rate, :validFrom, :validTo)
            """)
        .param("fundCode", fund.name())
        .param("feeType", feeType)
        .param("rate", new BigDecimal(rate))
        .param("validFrom", LocalDate.parse(validFrom))
        .param("validTo", validTo == null ? null : LocalDate.parse(validTo))
        .update();
  }
}
