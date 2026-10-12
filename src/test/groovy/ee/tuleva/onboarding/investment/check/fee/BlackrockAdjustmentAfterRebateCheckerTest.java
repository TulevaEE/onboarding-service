package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.investment.check.fee.FeeCheckScope.ALL;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.PASS;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckType.BLACKROCK_ADJUSTMENT_AFTER_REBATE;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.REBATE_ADJUSTMENT_DEADLINE_WORKING_DAYS;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.ADJUSTMENT;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.MANAGEMENT_FEE_REBATE;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import ee.tuleva.onboarding.ledger.LedgerEntryAmount;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlackrockAdjustmentAfterRebateCheckerTest {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");
  private static final String REBATE_ACCOUNT = "MANAGEMENT_FEE_REBATE:TUK75";
  private static final String RECEIVABLE_ACCOUNT = "BLACKROCK_ADJUSTMENT:TUK75";
  private static final int WORKING_DAYS_REPORTED_AFTER_DEADLINE = 5;

  private static final LocalDate MONDAY = LocalDate.of(2026, 6, 1);
  private static final LocalDate TUESDAY = LocalDate.of(2026, 6, 2);
  private static final LocalDate WEDNESDAY = LocalDate.of(2026, 6, 3);
  private static final LocalDate FRIDAY = LocalDate.of(2026, 6, 5);
  private static final LocalDate NEXT_MONDAY = LocalDate.of(2026, 6, 8);
  private static final LocalDate NEXT_TUESDAY = LocalDate.of(2026, 6, 9);
  private static final LocalDate NEXT_WEDNESDAY = LocalDate.of(2026, 6, 10);

  private static final LocalDate MONDAY_BEFORE_VICTORY_DAY_AND_MIDSUMMER =
      LocalDate.of(2026, 6, 22);
  private static final LocalDate THURSDAY_AFTER_MIDSUMMER = LocalDate.of(2026, 6, 25);
  private static final LocalDate FRIDAY_AFTER_MIDSUMMER = LocalDate.of(2026, 6, 26);

  @Mock private NavLedgerRepository navLedgerRepository;
  @Mock private InvestmentParameterRepository parameterRepository;

  private BlackrockAdjustmentAfterRebateChecker checker;

  @BeforeEach
  void setUp() {
    checker =
        new BlackrockAdjustmentAfterRebateChecker(
            navLedgerRepository,
            parameterRepository,
            new PublicHolidays(),
            WORKING_DAYS_REPORTED_AFTER_DEADLINE);
  }

  @Test
  void aRebateStillInsideItsWorkingDayOfGraceIsNotYetAWarning() {
    givenADeadlineOfWorkingDays(1);
    givenRebates(rebateBookedOn(MONDAY, "1234.56"));

    assertThat(checker.check(TUK75, TUESDAY)).containsExactly(pass());
  }

  @Test
  void aRebateTheReceivableWasNotSetAfterWarnsOnceItsWorkingDayOfGraceHasPassed() {
    givenADeadlineOfWorkingDays(1);
    givenRebates(rebateBookedOn(MONDAY, "1234.56"));

    assertThat(checker.check(TUK75, WEDNESDAY))
        .containsExactly(
            new FeeCheckFinding(
                TUK75,
                BLACKROCK_ADJUSTMENT_AFTER_REBATE,
                ALL,
                WARNING,
                "Rebate of 1234.56 booked to TUK75 on 2026-06-01, but BLACKROCK_ADJUSTMENT:TUK75"
                    + " has not been set since, 2 working days on, and stands at 0.00; if that"
                    + " still includes this rebate, the NAV counts it twice, in cash and in the"
                    + " receivable",
                new BigDecimal("1234.56"),
                List.of("rebate=2026-06-01/1234.56"),
                Map.of(
                    "bookingDate",
                    "2026-06-01",
                    "amount",
                    "1234.56",
                    "workingDaysSince",
                    2L,
                    "receivableBalance",
                    "0.00")));
  }

  @Test
  void settingTheReceivableAfterTheRebateClearsTheWarning() {
    givenADeadlineOfWorkingDays(1);
    var rebate = rebateBookedOn(MONDAY, "1234.56");
    givenRebates(rebate);
    givenTheReceivableWasSetAfter(rebate);

    assertThat(checker.check(TUK75, WEDNESDAY)).containsExactly(pass());
  }

  @Test
  void settingTheReceivableClearsOnlyTheRebatesItWasSetAfter() {
    givenADeadlineOfWorkingDays(1);
    var adjusted = rebateBookedOn(MONDAY, "1234.56");
    givenRebates(adjusted, rebateBookedOn(WEDNESDAY, "65.43"));
    givenTheReceivableWasSetAfter(adjusted);

    assertThat(checker.check(TUK75, FRIDAY))
        .singleElement()
        .extracting(FeeCheckFinding::identifiers)
        .isEqualTo(List.of("rebate=2026-06-03/65.43"));
  }

  @Test
  void asksWhetherTheReceivableWasSetAfterTheRebateWasRecordedForItsBookingDayOrLater() {
    givenADeadlineOfWorkingDays(1);
    var rebate = rebateBookedOn(MONDAY, "1234.56");
    givenRebates(rebate);

    checker.check(TUK75, WEDNESDAY);

    verify(navLedgerRepository)
        .existsTransactionRecordedAfter(
            RECEIVABLE_ACCOUNT, ADJUSTMENT, rebate.transactionId(), startOf(MONDAY));
  }

  @Test
  void eachUnadjustedRebateIsNamedInAFindingOfItsOwn() {
    givenADeadlineOfWorkingDays(1);
    givenRebates(rebateBookedOn(MONDAY, "1234.56"), rebateBookedOn(TUESDAY, "65.43"));

    assertThat(checker.check(TUK75, FRIDAY))
        .extracting(FeeCheckFinding::identifiers)
        .containsExactly(List.of("rebate=2026-06-01/1234.56"), List.of("rebate=2026-06-02/65.43"));
  }

  @Test
  void anUnadjustedRebateIsStillReportedOnTheLastWorkingDayOfItsReportingWindow() {
    givenADeadlineOfWorkingDays(1);
    givenRebates(rebateBookedOn(MONDAY, "1234.56"));

    assertThat(severities(NEXT_TUESDAY)).containsExactly(WARNING);
  }

  @Test
  void anUnadjustedRebateAgesOutOfItsReportingWindowAndClearsWithoutAZeroAdjustmentBeingWritten() {
    givenADeadlineOfWorkingDays(1);
    givenRebates(rebateBookedOn(MONDAY, "1234.56"));

    assertThat(checker.check(TUK75, NEXT_WEDNESDAY)).containsExactly(pass());
  }

  @Test
  void theWorkingDayOfGraceDoesNotCountTheWeekend() {
    givenADeadlineOfWorkingDays(1);
    givenRebates(rebateBookedOn(FRIDAY, "1234.56"));

    assertThat(severities(NEXT_MONDAY)).containsExactly(PASS);
    assertThat(severities(NEXT_TUESDAY)).containsExactly(WARNING);
  }

  @Test
  void theWorkingDayOfGraceDoesNotCountPublicHolidays() {
    givenADeadlineOfWorkingDays(1);
    givenRebates(rebateBookedOn(MONDAY_BEFORE_VICTORY_DAY_AND_MIDSUMMER, "1234.56"));

    assertThat(severities(THURSDAY_AFTER_MIDSUMMER)).containsExactly(PASS);
    assertThat(severities(FRIDAY_AFTER_MIDSUMMER)).containsExactly(WARNING);
  }

  @Test
  void theDeadlineIsTheOneInForceInInvestmentParameterOnTheCheckDate() {
    givenADeadlineOfWorkingDays(2);
    givenRebates(rebateBookedOn(MONDAY, "1234.56"));

    assertThat(severities(WEDNESDAY)).containsExactly(PASS);
  }

  @Test
  void aDebitOnTheRebateAccountIsNotARebateLanding() {
    givenADeadlineOfWorkingDays(1);
    givenRebates(entryBookedOn(MONDAY, "1234.56"));

    assertThat(checker.check(TUK75, WEDNESDAY)).containsExactly(pass());
  }

  @Test
  void aFundWithNoRebatesPasses() {
    givenADeadlineOfWorkingDays(1);

    assertThat(checker.check(TUK75, WEDNESDAY)).containsExactly(pass());
  }

  @Test
  void asksForTheFundsRebatesBackToTheFirstBookingDayStillInsideTheReportingWindow() {
    givenADeadlineOfWorkingDays(1);

    checker.check(TUK75, NEXT_WEDNESDAY);

    verify(navLedgerRepository)
        .findEntriesByTransactionTypeBetween(
            REBATE_ACCOUNT, MANAGEMENT_FEE_REBATE, startOf(TUESDAY), startOf(NEXT_WEDNESDAY));
  }

  private List<FeeCheckSeverity> severities(LocalDate checkDate) {
    return checker.check(TUK75, checkDate).stream().map(FeeCheckFinding::severity).toList();
  }

  private void givenADeadlineOfWorkingDays(int workingDays) {
    given(parameterRepository.findLatestValue(eq(REBATE_ADJUSTMENT_DEADLINE_WORKING_DAYS), any()))
        .willReturn(BigDecimal.valueOf(workingDays));
  }

  private void givenRebates(LedgerEntryAmount... rebates) {
    lenient()
        .when(navLedgerRepository.getSystemAccountBalance(RECEIVABLE_ACCOUNT))
        .thenReturn(new BigDecimal("0.00"));
    given(
            navLedgerRepository.findEntriesByTransactionTypeBetween(
                eq(REBATE_ACCOUNT), eq(MANAGEMENT_FEE_REBATE), any(), any()))
        .willReturn(List.of(rebates));
  }

  private void givenTheReceivableWasSetAfter(LedgerEntryAmount rebate) {
    given(
            navLedgerRepository.existsTransactionRecordedAfter(
                eq(RECEIVABLE_ACCOUNT), eq(ADJUSTMENT), eq(rebate.transactionId()), any()))
        .willReturn(true);
  }

  private static LedgerEntryAmount rebateBookedOn(LocalDate bookingDate, String received) {
    return entryBookedOn(bookingDate, "-" + received);
  }

  private static LedgerEntryAmount entryBookedOn(LocalDate bookingDate, String amount) {
    return new LedgerEntryAmount(
        UUID.randomUUID(),
        bookingDate.atTime(LocalTime.MAX).atZone(ESTONIAN_ZONE).toInstant(),
        new BigDecimal(amount));
  }

  private static Instant startOf(LocalDate date) {
    return date.atStartOfDay(ESTONIAN_ZONE).toInstant();
  }

  private static FeeCheckFinding pass() {
    return FeeCheckFinding.pass(TUK75, BLACKROCK_ADJUSTMENT_AFTER_REBATE, ALL);
  }
}
