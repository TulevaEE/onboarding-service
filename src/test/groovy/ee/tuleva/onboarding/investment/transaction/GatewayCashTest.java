package ee.tuleva.onboarding.investment.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class GatewayCashTest {

  @Test
  void aReportWithoutABookedBalanceSaysSoInTheAudit() {
    assertThat(new GatewayCash.NoBookedBalance().payload())
        .isEqualTo(Map.of("status", "NO_BOOKED_BALANCE"));
  }

  @Test
  void aFundWithoutAnInvestmentAccountSaysSoInTheAudit() {
    assertThat(new GatewayCash.NoInvestmentAccount().payload())
        .isEqualTo(Map.of("status", "NO_INVESTMENT_ACCOUNT"));
  }
}
