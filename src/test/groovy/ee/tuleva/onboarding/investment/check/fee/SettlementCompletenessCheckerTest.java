package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.FAIL;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.NOT_RUN;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.PASS;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.WARNING;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.FEE_ACCRUAL;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.FEE_SETTLEMENT;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.deadline.BusinessDays;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.fees.FeeAccrualRepository;
import ee.tuleva.onboarding.investment.fees.FeeType;
import ee.tuleva.onboarding.ledger.LedgerEntryAmount;
import ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementCompletenessCheckerTest {

  private static final LocalDate FEE_MONTH = LocalDate.of(2026, 5, 1);
  private static final LocalDate THIRD_BUSINESS_DAY_OF_JUNE = LocalDate.of(2026, 6, 3);
  private static final LocalDate PAST_THE_GRACE_WINDOW = LocalDate.of(2026, 6, 9);
  private static final BigDecimal ACCRUED = new BigDecimal("-1234.56");
  private static final BigDecimal SETTLED = new BigDecimal("1234.56");

  @Mock private FeeAccrualRepository feeAccrualRepository;
  @Mock private NavLedgerRepository navLedgerRepository;

  private SettlementCompletenessChecker checker;

  @BeforeEach
  void setUp() {
    checker =
        new SettlementCompletenessChecker(
            feeAccrualRepository, navLedgerRepository, new BusinessDays(new PublicHolidays()), 5);
  }

  @Test
  void aFullySettledMonthPasses() {
    givenMonthCrossed();
    givenBalances(ZERO, ZERO);
    givenEntries(FEE_ACCRUAL, ACCRUED);
    givenEntries(FEE_SETTLEMENT, SETTLED);

    assertThat(check(THIRD_BUSINESS_DAY_OF_JUNE))
        .extracting(FeeCheckFinding::severity)
        .containsOnly(PASS);
  }

  @Test
  void aResidualLeftOnTheAccrualAccountIsDetected() {
    var residual = new BigDecimal("13.32");
    givenMonthCrossed();
    givenBalances(ZERO, residual);
    givenEntries(FEE_ACCRUAL, ACCRUED);
    givenEntries(FEE_SETTLEMENT, SETTLED.add(residual));

    assertThat(failures()).anyMatch(finding -> finding.deviationAmount().compareTo(residual) == 0);
  }

  @Test
  void aCorrectionIntoAnAlreadySettledMonthNamesTheSourceMonth() {
    var misattributed = new BigDecimal("13.32");
    givenMonthCrossed();
    givenBalances(misattributed, ZERO);
    givenEntries(FEE_ACCRUAL, ACCRUED);
    givenEntries(FEE_SETTLEMENT, SETTLED.subtract(misattributed));

    assertThat(failures())
        .anyMatch(
            finding ->
                finding.message().contains(FEE_MONTH.minusMonths(1).toString())
                    && finding.deviationAmount().compareTo(misattributed) == 0);
  }

  @Test
  void anOrphanSettlementLeftAfterAccrualsWereDeletedIsDetected() {
    givenMonthCrossed();
    givenBalances(ZERO, SETTLED);
    givenEntries(FEE_ACCRUAL);
    givenEntries(FEE_SETTLEMENT, SETTLED);

    assertThat(failures()).isNotEmpty();
  }

  @Test
  void aMonthThatAccruedNothingExpectsNoSettlementAndPasses() {
    givenMonthCrossed();
    givenBalances(ZERO, ZERO);
    givenEntries(FEE_ACCRUAL);
    givenEntries(FEE_SETTLEMENT);

    assertThat(check(THIRD_BUSINESS_DAY_OF_JUNE))
        .extracting(FeeCheckFinding::severity)
        .containsOnly(PASS);
  }

  @Test
  void twoSettlementsForOneMonthAreDetected() {
    givenMonthCrossed();
    givenBalances(ZERO, ZERO);
    givenEntries(FEE_ACCRUAL, ACCRUED);
    givenEntries(FEE_SETTLEMENT, new BigDecimal("600.00"), new BigDecimal("634.56"));

    assertThat(failures()).isNotEmpty();
  }

  @Test
  void aMonthNotYetCrossedIsNotRunRatherThanAFailure() {
    givenMonthNotCrossed();

    assertThat(check(THIRD_BUSINESS_DAY_OF_JUNE))
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

  @Test
  void theDepotAccrualIsRecordedWithoutRaisingAFinding() {
    givenMonthCrossed();
    givenBalances(ZERO, ZERO);
    givenEntries(FEE_ACCRUAL, ACCRUED);
    givenEntries(FEE_SETTLEMENT, SETTLED);

    var depot =
        check(THIRD_BUSINESS_DAY_OF_JUNE).stream()
            .filter(finding -> finding.scope() == FeeCheckScope.DEPOT)
            .findFirst()
            .orElseThrow();

    assertThat(depot.severity()).isEqualTo(PASS);
    assertThat(depot.details())
        .containsEntry("accrued", ACCRUED.toPlainString())
        .containsEntry("settled", SETTLED.toPlainString())
        .doesNotContainKey("grossAccrued");
  }

  private List<FeeCheckFinding> check(LocalDate checkDate) {
    return checker.check(TUK75, FEE_MONTH, checkDate);
  }

  private List<FeeCheckFinding> failures() {
    return check(THIRD_BUSINESS_DAY_OF_JUNE).stream()
        .filter(finding -> finding.severity() == FAIL)
        .toList();
  }

  private void givenMonthCrossed() {
    given(feeAccrualRepository.existsByFundAndFeeMonth(TUK75, FEE_MONTH.plusMonths(1)))
        .willReturn(true);
  }

  private void givenMonthNotCrossed() {
    given(feeAccrualRepository.existsByFundAndFeeMonth(TUK75, FEE_MONTH.plusMonths(1)))
        .willReturn(false);
  }

  private void givenBalances(BigDecimal opening, BigDecimal closing) {
    Arrays.stream(FeeType.values())
        .forEach(
            feeType -> {
              var account = feeType.getAccrualAccount().getAccountName(TUK75);
              given(
                      navLedgerRepository.getSystemAccountBalanceBefore(
                          eq(account), eq(startOfMonth())))
                  .willReturn(opening);
              given(
                      navLedgerRepository.getSystemAccountBalanceBefore(
                          eq(account), eq(endOfMonth())))
                  .willReturn(closing);
            });
  }

  private void givenEntries(TransactionType transactionType, BigDecimal... amounts) {
    var entries =
        Arrays.stream(amounts)
            .map(amount -> new LedgerEntryAmount(UUID.randomUUID(), startOfMonth(), amount))
            .toList();
    given(
            navLedgerRepository.findEntriesByTransactionTypeBetween(
                anyString(), eq(transactionType), any(), any()))
        .willReturn(entries);
  }

  private Instant startOfMonth() {
    return FEE_MONTH.atStartOfDay(java.time.ZoneId.of("Europe/Tallinn")).toInstant();
  }

  private Instant endOfMonth() {
    return FEE_MONTH.plusMonths(1).atStartOfDay(java.time.ZoneId.of("Europe/Tallinn")).toInstant();
  }
}
