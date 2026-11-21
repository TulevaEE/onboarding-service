package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.FAIL;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.NOT_RUN;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.PASS;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckType.SETTLEMENT_COMPLETENESS;
import static ee.tuleva.onboarding.investment.fees.FeeType.DEPOT;
import static ee.tuleva.onboarding.investment.fees.FeeType.MANAGEMENT;
import static ee.tuleva.onboarding.ledger.SystemAccount.DEPOT_FEE_ACCRUAL;
import static ee.tuleva.onboarding.ledger.SystemAccount.MANAGEMENT_FEE_ACCRUAL;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.deadline.BusinessDays;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.fees.FeeAccrualRepository;
import ee.tuleva.onboarding.investment.fees.FeeChargedToFundPolicy;
import ee.tuleva.onboarding.investment.fees.FeeChargedToFundPolicy.Policy;
import ee.tuleva.onboarding.investment.fees.FeeChargedToFundPolicy.Resolver;
import ee.tuleva.onboarding.investment.fees.FeeMonthBalance;
import ee.tuleva.onboarding.investment.fees.FeeMonthPayment;
import ee.tuleva.onboarding.investment.fees.OutstandingFees;
import ee.tuleva.onboarding.ledger.FeeMonthSettlement;
import ee.tuleva.onboarding.ledger.NavFeeAccrualLedger;
import ee.tuleva.onboarding.ledger.SystemAccount;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementCompletenessCheckerTest {

  private static final LocalDate FEE_MONTH = LocalDate.of(2026, 11, 1);
  private static final LocalDate RECONCILED_THROUGH = LocalDate.of(2026, 12, 2);
  private static final LocalDate THIRD_BUSINESS_DAY_OF_DECEMBER = LocalDate.of(2026, 12, 3);
  private static final LocalDate PAST_THE_GRACE_WINDOW = LocalDate.of(2026, 12, 9);
  private static final Resolver MANAGEMENT_POLICY =
      new Resolver(TUK75, MANAGEMENT, List.of(new Policy(true, LocalDate.of(2017, 3, 28), null)));

  @Mock private FeeAccrualRepository feeAccrualRepository;
  @Mock private NavFeeAccrualLedger navFeeAccrualLedger;
  @Mock private OutstandingFees outstandingFees;
  @Mock private FeeChargedToFundPolicy feeChargedToFundPolicy;

  private SettlementCompletenessChecker checker;

  @BeforeEach
  void setUp() {
    checker =
        new SettlementCompletenessChecker(
            feeAccrualRepository,
            navFeeAccrualLedger,
            outstandingFees,
            feeChargedToFundPolicy,
            new BusinessDays(new PublicHolidays()),
            5);
  }

  @Test
  void aMonthReleasedAtItsEndPassesWhenReleasedForWhatItAccrued() {
    givenMonthCrossed();
    givenReleasedAtMonthEnd("1234.56", "1234.56");
    givenNothingInTheDepotFee();

    assertThat(check(THIRD_BUSINESS_DAY_OF_DECEMBER))
        .extracting(FeeCheckFinding::severity)
        .containsOnly(PASS);
  }

  @Test
  void aMonthEndReleaseThatNeverHappenedFails() {
    givenMonthCrossed();
    givenReleasedAtMonthEnd("1234.56", "0");
    givenNothingInTheDepotFee();

    assertThat(management(check(THIRD_BUSINESS_DAY_OF_DECEMBER)))
        .extracting(FeeCheckFinding::severity, FeeCheckFinding::deviationAmount)
        .containsExactly(FAIL, new BigDecimal("1234.56"));
  }

  @Test
  void aCorrectionBookedAfterTheReleaseFailsWithWhatItLeftOnTheAccrualAccount() {
    givenMonthCrossed();
    givenReleasedAtMonthEnd("1221.24", "1234.56");
    givenNothingInTheDepotFee();

    assertThat(management(check(THIRD_BUSINESS_DAY_OF_DECEMBER)))
        .isEqualTo(
            new FeeCheckFinding(
                TUK75,
                SETTLEMENT_COMPLETENESS,
                FeeCheckScope.MANAGEMENT,
                FAIL,
                "MANAGEMENT fee month 2026-11-01 accrued 1221.24 but was released for 1234.56",
                new BigDecimal("13.32"),
                List.of("settledAmount"),
                Map.of("feeMonth", "2026-11-01", "accrued", "1221.24", "settled", "1234.56")));
  }

  @Test
  void aMonthStillWaitingForItsDebitIsNotRunWhenTheLedgerOwesWhatTheNavOwesToTheRounding() {
    givenMonthCrossed();
    givenHeldUntilPaid("1234.57", "0", unpaid("1234.56"));
    givenNothingInTheDepotFee();

    assertThat(management(check(THIRD_BUSINESS_DAY_OF_DECEMBER)))
        .extracting(FeeCheckFinding::severity, FeeCheckFinding::identifiers)
        .containsExactly(NOT_RUN, List.of("awaitingDebit"));
  }

  @Test
  void aCorrectionBookedOnlyToTheLedgerOfAnUnpaidMonthFails() {
    givenMonthCrossed();
    givenHeldUntilPaid("900.00", "0", unpaid("1000.00"));
    givenNothingInTheDepotFee();

    assertThat(management(check(THIRD_BUSINESS_DAY_OF_DECEMBER)))
        .isEqualTo(
            new FeeCheckFinding(
                TUK75,
                SETTLEMENT_COMPLETENESS,
                FeeCheckScope.MANAGEMENT,
                FAIL,
                "MANAGEMENT fee month 2026-11-01: the ledger owes 900.00 but the NAV owes 1000.00",
                new BigDecimal("100.00"),
                List.of("ledgerDiffersFromNav"),
                Map.of(
                    "feeMonth", "2026-11-01",
                    "accrued", "900.00",
                    "settled", "0",
                    "ledgerOwes", "900.00",
                    "navOwes", "1000.00")));
  }

  @Test
  void aMonthItsDebitPaidPassesOnceTheLedgerSettledIt() {
    givenMonthCrossed();
    givenHeldUntilPaid("1234.57", "1234.57", cleared("1234.56"));
    givenNothingInTheDepotFee();

    assertThat(check(THIRD_BUSINESS_DAY_OF_DECEMBER))
        .extracting(FeeCheckFinding::severity)
        .containsOnly(PASS);
  }

  @Test
  void aMonthItsDebitPaidFailsWhileTheLedgerHasNotSettledIt() {
    givenMonthCrossed();
    givenHeldUntilPaid("1234.57", "0", cleared("1234.56"));
    givenNothingInTheDepotFee();

    assertThat(management(check(THIRD_BUSINESS_DAY_OF_DECEMBER)))
        .extracting(FeeCheckFinding::severity, FeeCheckFinding::deviationAmount)
        .containsExactly(FAIL, new BigDecimal("1234.57"));
  }

  @Test
  void aPartlyPaidMonthPassesWhenTheLedgerReleasedWhatWasPaid() {
    givenMonthCrossed();
    givenHeldUntilPaid(
        "1234.57",
        "1000.00",
        new FeeMonthBalance(
            FEE_MONTH,
            new BigDecimal("1234.56"),
            new BigDecimal("1000.00"),
            new BigDecimal("234.56"),
            null,
            List.of(new FeeMonthPayment(RECONCILED_THROUGH, new BigDecimal("234.56")))));
    givenNothingInTheDepotFee();

    assertThat(check(THIRD_BUSINESS_DAY_OF_DECEMBER))
        .extracting(FeeCheckFinding::severity)
        .containsOnly(PASS);
  }

  @Test
  void aMonthNotYetCrossedIsNotRunRatherThanAFailure() {
    givenMonthNotCrossed();

    assertThat(check(THIRD_BUSINESS_DAY_OF_DECEMBER))
        .extracting(FeeCheckFinding::severity)
        .containsOnly(NOT_RUN);
  }

  @Test
  void aPipelineStillStalledPastTheGraceWindowEscalatesToWarning() {
    givenMonthNotCrossed();

    assertThat(check(PAST_THE_GRACE_WINDOW))
        .extracting(FeeCheckFinding::severity)
        .containsOnly(WARNING);
  }

  private List<FeeCheckFinding> check(LocalDate checkDate) {
    return checker.check(TUK75, FEE_MONTH, checkDate);
  }

  private static FeeCheckFinding management(List<FeeCheckFinding> findings) {
    return findings.stream()
        .filter(finding -> finding.scope() == FeeCheckScope.MANAGEMENT)
        .findFirst()
        .orElseThrow();
  }

  private void givenMonthCrossed() {
    given(feeAccrualRepository.existsByFundAndFeeMonth(TUK75, FEE_MONTH.plusMonths(1)))
        .willReturn(true);
  }

  private void givenMonthNotCrossed() {
    given(feeAccrualRepository.existsByFundAndFeeMonth(TUK75, FEE_MONTH.plusMonths(1)))
        .willReturn(false);
  }

  private void givenReleasedAtMonthEnd(String accrued, String settled) {
    given(outstandingFees.releasedAtMonthEnd(MANAGEMENT, FEE_MONTH)).willReturn(true);
    givenLedger(MANAGEMENT_FEE_ACCRUAL, accrued, settled);
  }

  private void givenHeldUntilPaid(String accrued, String settled, FeeMonthBalance month) {
    given(outstandingFees.releasedAtMonthEnd(MANAGEMENT, FEE_MONTH)).willReturn(false);
    givenLedger(MANAGEMENT_FEE_ACCRUAL, accrued, settled);
    given(feeAccrualRepository.findLatestAccrualDate(TUK75))
        .willReturn(Optional.of(RECONCILED_THROUGH));
    given(feeChargedToFundPolicy.resolverFor(TUK75, MANAGEMENT)).willReturn(MANAGEMENT_POLICY);
    given(outstandingFees.monthsOn(MANAGEMENT_POLICY, RECONCILED_THROUGH))
        .willReturn(List.of(month));
  }

  private void givenNothingInTheDepotFee() {
    given(outstandingFees.releasedAtMonthEnd(DEPOT, FEE_MONTH)).willReturn(true);
    givenLedger(DEPOT_FEE_ACCRUAL, "0", "0");
  }

  private void givenLedger(SystemAccount account, String accrued, String settled) {
    given(navFeeAccrualLedger.accruedIn(TUK75, FEE_MONTH, account))
        .willReturn(new BigDecimal(accrued));
    given(navFeeAccrualLedger.settlementOf(TUK75, FEE_MONTH, account))
        .willReturn(
            new FeeMonthSettlement(
                settled.equals("0")
                    ? List.of()
                    : List.of(
                        new FeeMonthSettlement.Revision(
                            RECONCILED_THROUGH, new BigDecimal(settled)))));
  }

  private static FeeMonthBalance unpaid(String accrued) {
    return new FeeMonthBalance(
        FEE_MONTH, new BigDecimal(accrued), ZERO, new BigDecimal(accrued), null, List.of());
  }

  private static FeeMonthBalance cleared(String accrued) {
    return new FeeMonthBalance(
        FEE_MONTH,
        new BigDecimal(accrued),
        new BigDecimal(accrued),
        ZERO,
        RECONCILED_THROUGH,
        List.of(new FeeMonthPayment(RECONCILED_THROUGH, ZERO)));
  }
}
