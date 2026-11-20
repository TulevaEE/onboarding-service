package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.investment.fees.FeeType.DEPOT;
import static ee.tuleva.onboarding.investment.fees.FeeType.MANAGEMENT;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.investment.fees.FeeChargedToFundPolicy.Policy;
import ee.tuleva.onboarding.investment.fees.FeeChargedToFundPolicy.Resolver;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutstandingFeesTest {

  private static final LocalDate FUND_START = LocalDate.of(2017, 3, 28);
  private static final LocalDate OCTOBER_3 = LocalDate.of(2026, 10, 3);

  @Mock private FeeAccrualRepository feeAccrualRepository;

  @InjectMocks private OutstandingFees outstandingFees;

  @Test
  void owesTheMonthsChargedAccrualsWithAnExactHalfCentRoundedUp() {
    givenAccruals("100.002", "200.003");

    assertThat(outstandingFees.owedOn(charged(MANAGEMENT), OCTOBER_3)).isEqualTo("300.01");
  }

  @Test
  void owesTheMonthsChargedAccrualsWithLessThanHalfACentRoundedDown() {
    givenAccruals("100.001", "200.003");

    assertThat(outstandingFees.owedOn(charged(MANAGEMENT), OCTOBER_3)).isEqualTo("300.00");
  }

  @Test
  void owesNothingForAFeeTulevaPaysItself() {
    given(feeAccrualRepository.getUnsettledAccrualByDate(TUK75, DEPOT, OCTOBER_3))
        .willReturn(Map.of(LocalDate.of(2026, 10, 1), new BigDecimal("12.34")));

    assertThat(outstandingFees.owedOn(notCharged(DEPOT), OCTOBER_3)).isEqualTo(ZERO);
  }

  @Test
  void owesNothingBeforeTheMonthsFirstAccrual() {
    given(feeAccrualRepository.getUnsettledAccrualByDate(TUK75, MANAGEMENT, OCTOBER_3))
        .willReturn(Map.of());

    assertThat(outstandingFees.owedOn(charged(MANAGEMENT), OCTOBER_3)).isEqualTo(ZERO);
  }

  private void givenAccruals(String october1, String october2) {
    given(feeAccrualRepository.getUnsettledAccrualByDate(TUK75, MANAGEMENT, OCTOBER_3))
        .willReturn(
            Map.of(
                LocalDate.of(2026, 10, 1), new BigDecimal(october1),
                LocalDate.of(2026, 10, 2), new BigDecimal(october2)));
  }

  private static Resolver charged(FeeType feeType) {
    return new Resolver(TUK75, feeType, List.of(new Policy(true, FUND_START, null)));
  }

  private static Resolver notCharged(FeeType feeType) {
    return new Resolver(TUK75, feeType, List.of(new Policy(false, FUND_START, null)));
  }
}
