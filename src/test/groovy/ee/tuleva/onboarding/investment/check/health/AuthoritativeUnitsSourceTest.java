package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.analytics.transaction.fundbalance.FundBalance;
import ee.tuleva.onboarding.analytics.transaction.fundbalance.FundBalanceRepository;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthoritativeUnitsSourceTest {

  @Mock FundBalanceRepository fundBalanceRepository;
  @Mock NavLedgerRepository navLedgerRepository;

  @InjectMocks AuthoritativeUnitsSource source;

  @Test
  void resolvesLedgerSystemAccountBalanceForSavingsFund() {
    given(navLedgerRepository.getSystemAccountBalance("FUND_UNITS_OUTSTANDING:TKF100"))
        .willReturn(new BigDecimal("100005.50000"));

    var result = source.resolve(TKF100);

    assertThat(result).contains(new BigDecimal("100005.50000"));
  }

  @Test
  void sumsCountUnitsAndCountUnitsFmForPillarTwoFund() {
    var fundBalance =
        FundBalance.builder()
            .isin("EE3600109435")
            .countUnits(new BigDecimal("9000000"))
            .countUnitsFm(new BigDecimal("123.45"))
            .build();
    given(fundBalanceRepository.findFirstByIsinOrderByRequestDateDesc("EE3600109435"))
        .willReturn(Optional.of(fundBalance));

    var result = source.resolve(TUK75);

    assertThat(result).contains(new BigDecimal("9000123.45"));
  }

  @Test
  void emptyWhenNoFundBalanceForPillarTwoFund() {
    given(fundBalanceRepository.findFirstByIsinOrderByRequestDateDesc("EE3600109435"))
        .willReturn(Optional.empty());

    var result = source.resolve(TUK75);

    assertThat(result).isEmpty();
  }
}
