package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.PASS;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.WARNING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.investment.fees.FeeAccrualRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccrualRoundingDriftCheckerTest {

  private static final LocalDate FEE_MONTH = LocalDate.of(2026, 5, 1);
  private static final BigDecimal MONTH_TOTAL = new BigDecimal("1234.56");

  @Mock private FeeAccrualRepository feeAccrualRepository;

  private AccrualRoundingDriftChecker checker;

  @BeforeEach
  void setUp() {
    checker = new AccrualRoundingDriftChecker(feeAccrualRepository, new BigDecimal("0.25"));
  }

  @Test
  void driftWithinTheExpectedPerDayRoundingBoundPasses() {
    givenTotals(MONTH_TOTAL, MONTH_TOTAL.add(new BigDecimal("0.16")));

    assertThat(check()).extracting(FeeCheckFinding::severity).containsOnly(PASS);
  }

  @Test
  void driftBeyondWhatDailyRoundingCanExplainIsReported() {
    var drift = new BigDecimal("1.40");
    givenTotals(MONTH_TOTAL, MONTH_TOTAL.add(drift));

    var finding = check().getFirst();

    assertThat(finding.severity()).isEqualTo(WARNING);
    assertThat(finding.deviationAmount()).isEqualByComparingTo(drift);
  }

  @Test
  void identicalTotalsOnBothSidesPass() {
    givenTotals(MONTH_TOTAL, MONTH_TOTAL);

    assertThat(check()).extracting(FeeCheckFinding::severity).containsOnly(PASS);
  }

  private List<FeeCheckFinding> check() {
    return checker.check(TUK75, FEE_MONTH);
  }

  private void givenTotals(BigDecimal sumOfRounded, BigDecimal roundedSum) {
    given(feeAccrualRepository.sumRoundedDailyNetForMonth(any(), any(), any()))
        .willReturn(sumOfRounded);
    given(feeAccrualRepository.roundedSumOfDailyNetForMonth(any(), any(), any()))
        .willReturn(roundedSum);
  }
}
