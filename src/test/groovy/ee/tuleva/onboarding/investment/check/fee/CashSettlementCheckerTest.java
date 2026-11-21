package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckType.CASH_SETTLEMENT_OBSERVED;
import static ee.tuleva.onboarding.investment.fees.FeeType.MANAGEMENT;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.fees.FeeChargedToFundPolicy;
import ee.tuleva.onboarding.investment.fees.FeeChargedToFundPolicy.Policy;
import ee.tuleva.onboarding.investment.fees.FeeChargedToFundPolicy.Resolver;
import ee.tuleva.onboarding.investment.fees.FeeMonthBalance;
import ee.tuleva.onboarding.investment.fees.FeeMonthPayment;
import ee.tuleva.onboarding.investment.fees.OutstandingFees;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CashSettlementCheckerTest {

  private static final LocalDate SEPTEMBER = LocalDate.of(2026, 9, 1);
  private static final LocalDate OCTOBER = LocalDate.of(2026, 10, 1);
  private static final LocalDate DUE_DATE = LocalDate.of(2026, 10, 1);
  private static final LocalDate MORNING_AFTER_THE_DUE_DATE = LocalDate.of(2026, 10, 2);
  private static final Resolver MANAGEMENT_POLICY =
      new Resolver(TUK75, MANAGEMENT, List.of(new Policy(true, LocalDate.of(2017, 3, 28), null)));

  @Mock private OutstandingFees outstandingFees;
  @Mock private FeeChargedToFundPolicy feeChargedToFundPolicy;

  private CashSettlementChecker checker;

  @BeforeEach
  void setUp() {
    checker =
        new CashSettlementChecker(outstandingFees, feeChargedToFundPolicy, new PublicHolidays());
    given(feeChargedToFundPolicy.resolverFor(TUK75, MANAGEMENT)).willReturn(MANAGEMENT_POLICY);
  }

  @Test
  void passesOnceTheDebitHasClearedEveryCompleteMonth() {
    givenMonths(
        DUE_DATE,
        month(SEPTEMBER, "10000.00", "10000.01", "0", DUE_DATE),
        month(OCTOBER, "333.33", "0", "333.33", null));

    assertThat(checker.check(TUK75, MORNING_AFTER_THE_DUE_DATE))
        .containsExactly(
            FeeCheckFinding.pass(TUK75, CASH_SETTLEMENT_OBSERVED, FeeCheckScope.MANAGEMENT));
  }

  @Test
  void warnsTheMorningAfterTheDueDateWhenNoDebitHasCome() {
    givenMonths(
        DUE_DATE,
        month(SEPTEMBER, "10000.00", "0", "10000.00", null),
        month(OCTOBER, "333.33", "0", "333.33", null));

    assertThat(checker.check(TUK75, MORNING_AFTER_THE_DUE_DATE))
        .containsExactly(
            new FeeCheckFinding(
                TUK75,
                CASH_SETTLEMENT_OBSERVED,
                FeeCheckScope.MANAGEMENT,
                WARNING,
                "No debit for the 2026-09-01 management fee by 2026-10-01: 10000.00 stays owed in"
                    + " the NAV. Ask SEB why it was not debited",
                new BigDecimal("10000.00"),
                List.of("2026-09-01 noDebit"),
                Map.of("positionDate", "2026-10-01")));
  }

  @Test
  void warnsWhenADebitBeyondTheThresholdLeftPartOfTheMonthOwed() {
    givenMonths(
        DUE_DATE,
        month(SEPTEMBER, "10000.00", "9000.00", "1000.00", null),
        month(OCTOBER, "333.33", "0", "333.33", null));

    assertThat(checker.check(TUK75, MORNING_AFTER_THE_DUE_DATE))
        .containsExactly(
            new FeeCheckFinding(
                TUK75,
                CASH_SETTLEMENT_OBSERVED,
                FeeCheckScope.MANAGEMENT,
                WARNING,
                "The 2026-09-01 management fee debit was 9000.00 against 10000.00 accrued: 1000.00"
                    + " stays owed in the NAV. Ask SEB to debit the difference",
                new BigDecimal("1000.00"),
                List.of("2026-09-01 debitDiffersFromAccrual"),
                Map.of("positionDate", "2026-10-01")));
  }

  @Test
  void warnsWhenADebitTookMoreThanTheMonthAccrued() {
    givenMonths(
        DUE_DATE,
        month(SEPTEMBER, "10000.00", "10500.00", "-500.00", DUE_DATE),
        month(OCTOBER, "333.33", "0", "333.33", null));

    assertThat(checker.check(TUK75, MORNING_AFTER_THE_DUE_DATE))
        .containsExactly(
            new FeeCheckFinding(
                TUK75,
                CASH_SETTLEMENT_OBSERVED,
                FeeCheckScope.MANAGEMENT,
                WARNING,
                "The 2026-09-01 management fee debit was 10500.00 against 10000.00 accrued: the"
                    + " fund is owed 500.00 back. Ask SEB to refund the excess",
                new BigDecimal("500.00"),
                List.of("2026-09-01 debitDiffersFromAccrual"),
                Map.of("positionDate", "2026-10-01")));
  }

  @Test
  void doesNotAskForTheCurrentMonthsDebit() {
    givenMonths(LocalDate.of(2026, 10, 19), month(OCTOBER, "333.33", "0", "333.33", null));

    assertThat(checker.check(TUK75, LocalDate.of(2026, 10, 20)))
        .extracting(FeeCheckFinding::severity)
        .containsExactly(FeeCheckSeverity.PASS);
  }

  private void givenMonths(LocalDate positionDate, FeeMonthBalance... months) {
    given(outstandingFees.monthsOn(MANAGEMENT_POLICY, positionDate)).willReturn(List.of(months));
  }

  private static FeeMonthBalance month(
      LocalDate feeMonth, String accrued, String paid, String owed, @Nullable LocalDate clearedOn) {
    return new FeeMonthBalance(
        feeMonth,
        new BigDecimal(accrued),
        new BigDecimal(paid),
        new BigDecimal(owed),
        clearedOn,
        new BigDecimal(paid).signum() == 0
            ? List.of()
            : List.of(new FeeMonthPayment(DUE_DATE, new BigDecimal(owed))));
  }
}
