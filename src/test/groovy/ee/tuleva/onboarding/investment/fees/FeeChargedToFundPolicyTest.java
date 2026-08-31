package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.fees.FeeType.DEPOT;
import static ee.tuleva.onboarding.investment.fees.FeeType.MANAGEMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
@Import(FeeChargedToFundPolicy.class)
class FeeChargedToFundPolicyTest {

  private static final LocalDate DATE = LocalDate.of(2026, 8, 12);

  @Autowired private JdbcClient jdbcClient;
  @Autowired private FeeChargedToFundPolicy policy;

  @Test
  void chargedToFund_throwsWhenTheFundAndFeeTypeAreNotConfiguredAtAll() {
    jdbcClient.sql("DELETE FROM investment_fee_policy").update();

    assertThatThrownBy(() -> policy.chargedToFund(TKF100, DEPOT, DATE))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void chargedToFund_throwsOnAGapBetweenRows() {
    jdbcClient.sql("DELETE FROM investment_fee_policy").update();
    insertPolicy(TUK75, DEPOT, false, LocalDate.of(2017, 3, 28), LocalDate.of(2026, 6, 30));
    insertPolicy(TUK75, DEPOT, true, LocalDate.of(2026, 9, 1), null);

    assertThatThrownBy(() -> policy.chargedToFund(TUK75, DEPOT, LocalDate.of(2026, 7, 15)))
        .isInstanceOf(IllegalStateException.class);
    assertThat(policy.chargedToFund(TUK75, DEPOT, LocalDate.of(2026, 6, 30))).isFalse();
    assertThat(policy.chargedToFund(TUK75, DEPOT, LocalDate.of(2026, 9, 1))).isTrue();
  }

  @Test
  void chargedToFund_throwsWhenRowsOverlapInsteadOfPickingOne() {
    jdbcClient.sql("DELETE FROM investment_fee_policy").update();
    insertPolicy(TUK75, DEPOT, false, LocalDate.of(2017, 3, 28), null);
    insertPolicy(TUK75, DEPOT, true, LocalDate.of(2026, 1, 1), null);

    assertThatThrownBy(() -> policy.chargedToFund(TUK75, DEPOT, DATE))
        .isInstanceOf(IllegalStateException.class);
    assertThat(policy.chargedToFund(TUK75, DEPOT, LocalDate.of(2025, 12, 31))).isFalse();
  }

  @Test
  void chargedToFund_migrationCoversEveryFundAndFeeType() {
    for (TulevaFund fund : TulevaFund.values()) {
      assertThat(policy.chargedToFund(fund, DEPOT, DATE)).isFalse();
      assertThat(policy.chargedToFund(fund, MANAGEMENT, DATE)).isTrue();
    }
  }

  @Test
  void chargedToFund_throwsWhenTheFirstRowStartsAfterTheFundDid() {
    jdbcClient.sql("DELETE FROM investment_fee_policy").update();
    insertPolicy(TUK75, DEPOT, false, TUK75.getInceptionDate().plusYears(1), null);

    assertThatThrownBy(
            () -> policy.chargedToFund(TUK75, DEPOT, TUK75.getInceptionDate().plusMonths(1)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void chargedToFund_readsDatesBeforeTheFundExistedAsTheFoundingPolicy() {
    assertThat(policy.chargedToFund(TUK75, DEPOT, LocalDate.of(2017, 3, 27))).isFalse();
    assertThat(policy.chargedToFund(TUK75, DEPOT, LocalDate.of(2017, 3, 28))).isFalse();
    assertThat(policy.chargedToFund(TUK75, MANAGEMENT, LocalDate.of(2017, 3, 27))).isTrue();
    assertThat(policy.chargedToFund(TUK75, MANAGEMENT, LocalDate.of(2017, 3, 28))).isTrue();
  }

  @Test
  void chargedToFund_followsTheRowValidOnTheGivenDate() {
    jdbcClient.sql("DELETE FROM investment_fee_policy").update();
    insertPolicy(TUK75, DEPOT, false, LocalDate.of(2017, 3, 28), LocalDate.of(2026, 12, 31));
    insertPolicy(TUK75, DEPOT, true, LocalDate.of(2027, 1, 1), null);

    assertThat(policy.chargedToFund(TUK75, DEPOT, LocalDate.of(2026, 12, 31))).isFalse();
    assertThat(policy.chargedToFund(TUK75, DEPOT, LocalDate.of(2027, 1, 1))).isTrue();
    assertThat(policy.chargedToFund(TUK75, DEPOT, LocalDate.of(2027, 6, 30))).isTrue();
  }

  private void insertPolicy(
      TulevaFund fund,
      FeeType feeType,
      boolean chargedToFund,
      LocalDate validFrom,
      LocalDate validTo) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_fee_policy
                (fund_code, fee_type, charged_to_fund, valid_from, valid_to, created_by)
            VALUES (:fundCode, :feeType, :chargedToFund, :validFrom, :validTo, 'TEST')
            """)
        .param("fundCode", fund.name())
        .param("feeType", feeType.name())
        .param("chargedToFund", chargedToFund)
        .param("validFrom", validFrom)
        .param("validTo", validTo)
        .update();
  }
}
