package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.fees.FeeType.DEPOT;
import static ee.tuleva.onboarding.investment.fees.FeeType.MANAGEMENT;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
@Import(FeeNavInclusionPolicy.class)
class FeeNavInclusionPolicyTest {

  private static final LocalDate DATE = LocalDate.of(2026, 8, 12);

  @Autowired private JdbcClient jdbcClient;
  @Autowired private FeeNavInclusionPolicy policy;

  @Test
  void includeInNav_defaultsToTrueWhenNoPolicyRowExists() {
    jdbcClient.sql("DELETE FROM investment_fee_policy").update();

    assertThat(policy.includeInNav(TKF100, DEPOT, DATE)).isTrue();
    assertThat(policy.includeInNav(TKF100, MANAGEMENT, DATE)).isTrue();
  }

  @Test
  void includeInNav_migrationExcludesDepotForEveryFund() {
    for (TulevaFund fund : TulevaFund.values()) {
      assertThat(policy.includeInNav(fund, DEPOT, DATE)).isFalse();
    }
  }

  @Test
  void includeInNav_excludesOnlyTheConfiguredFeeType() {
    assertThat(policy.includeInNav(TUK75, MANAGEMENT, DATE)).isTrue();
  }

  @Test
  void includeInNav_treatsDatesBeforeValidFromAsIncluded() {
    assertThat(policy.includeInNav(TUK75, DEPOT, LocalDate.of(2017, 3, 27))).isTrue();
    assertThat(policy.includeInNav(TUK75, DEPOT, LocalDate.of(2017, 3, 28))).isFalse();
  }

  @Test
  void includeInNav_followsTheRowValidOnTheGivenDate() {
    jdbcClient.sql("DELETE FROM investment_fee_policy").update();
    insertPolicy(TUK75, DEPOT, false, LocalDate.of(2017, 3, 28), LocalDate.of(2026, 12, 31));
    insertPolicy(TUK75, DEPOT, true, LocalDate.of(2027, 1, 1), null);

    assertThat(policy.includeInNav(TUK75, DEPOT, LocalDate.of(2026, 12, 31))).isFalse();
    assertThat(policy.includeInNav(TUK75, DEPOT, LocalDate.of(2027, 1, 1))).isTrue();
    assertThat(policy.includeInNav(TUK75, DEPOT, LocalDate.of(2027, 6, 30))).isTrue();
  }

  private void insertPolicy(
      TulevaFund fund,
      FeeType feeType,
      boolean includeInNav,
      LocalDate validFrom,
      LocalDate validTo) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_fee_policy
                (fund_code, fee_type, include_in_nav, valid_from, valid_to, created_by)
            VALUES (:fundCode, :feeType, :includeInNav, :validFrom, :validTo, 'TEST')
            """)
        .param("fundCode", fund.name())
        .param("feeType", feeType.name())
        .param("includeInNav", includeInNav)
        .param("validFrom", validFrom)
        .param("validTo", validTo)
        .update();
  }
}
