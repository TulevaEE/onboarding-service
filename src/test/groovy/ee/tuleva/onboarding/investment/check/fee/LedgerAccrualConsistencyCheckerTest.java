package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.FAIL;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.PASS;
import static ee.tuleva.onboarding.investment.fees.FeeType.MANAGEMENT;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.FEE_ACCRUAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.investment.fees.DailyAccrualAmount;
import ee.tuleva.onboarding.investment.fees.FeeAccrualRepository;
import ee.tuleva.onboarding.investment.fees.FeeChargedToFundPolicy;
import ee.tuleva.onboarding.ledger.LedgerEntryAmount;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerAccrualConsistencyCheckerTest {

  private static final LocalDate DAY_ONE = LocalDate.of(2026, 6, 2);
  private static final LocalDate DAY_TWO = LocalDate.of(2026, 6, 3);

  @Mock private FeeAccrualRepository feeAccrualRepository;
  @Mock private NavLedgerRepository navLedgerRepository;
  @Mock private FeeChargedToFundPolicy feeChargedToFundPolicy;

  private LedgerAccrualConsistencyChecker checker;

  @BeforeEach
  void setUp() {
    checker =
        new LedgerAccrualConsistencyChecker(
            feeAccrualRepository, navLedgerRepository, feeChargedToFundPolicy);
    givenChargedToFund(true);
  }

  private void givenChargedToFund(boolean chargedToFund) {
    given(feeChargedToFundPolicy.resolverFor(TUK75, MANAGEMENT))
        .willReturn(
            new FeeChargedToFundPolicy.Resolver(
                TUK75,
                MANAGEMENT,
                List.of(
                    new FeeChargedToFundPolicy.Policy(
                        chargedToFund, LocalDate.of(2017, 3, 28), (LocalDate) null))));
  }

  @Test
  void matchingDaysPass() {
    givenAccruals(accrual(DAY_ONE, "5.89"), accrual(DAY_TWO, "5.90"));
    givenLedger(ledger(DAY_ONE, "-5.89"), ledger(DAY_TWO, "-5.90"));

    var findings = check();

    assertThat(findings).singleElement().extracting(FeeCheckFinding::severity).isEqualTo(PASS);
  }

  @Test
  void aDayWhereTheTableMovedButTheLedgerDidNotFails() {
    givenAccruals(accrual(DAY_ONE, "10.89"));
    givenLedger(ledger(DAY_ONE, "-5.89"));

    var finding = check().getFirst();

    assertThat(finding.severity()).isEqualTo(FAIL);
    assertThat(finding.deviationAmount()).isEqualByComparingTo("5.00");
    assertThat(finding.message()).contains(DAY_ONE.toString());
  }

  @Test
  void aZeroAccrualDayWithNoLedgerEntryPasses() {
    givenAccruals(accrual(DAY_ONE, "0.00"));
    givenLedger();

    assertThat(check()).singleElement().extracting(FeeCheckFinding::severity).isEqualTo(PASS);
  }

  @Test
  void aZeroAccrualDayWithALedgerEntryFails() {
    givenAccruals(accrual(DAY_ONE, "0.00"));
    givenLedger(ledger(DAY_ONE, "-5.89"));

    var finding = check().getFirst();

    assertThat(finding.severity()).isEqualTo(FAIL);
    assertThat(finding.deviationAmount()).isEqualByComparingTo("5.89");
  }

  @Test
  void aLedgerEntryWithNoAccrualRowFails() {
    givenAccruals();
    givenLedger(ledger(DAY_ONE, "-5.89"));

    var finding = check().getFirst();

    assertThat(finding.severity()).isEqualTo(FAIL);
    assertThat(finding.deviationAmount()).isEqualByComparingTo("5.89");
  }

  @Test
  void twoLedgerEntriesForOneAccrualDayFail() {
    givenAccruals(accrual(DAY_ONE, "5.89"));
    givenLedger(ledger(DAY_ONE, "-5.89"), ledger(DAY_ONE, "-5.89"));

    assertThat(check().getFirst().severity()).isEqualTo(FAIL);
  }

  @Test
  void multipleDivergentDaysAreReportedAsOneFindingWithTheTotalDeviation() {
    givenAccruals(accrual(DAY_ONE, "10.89"), accrual(DAY_TWO, "8.90"));
    givenLedger(ledger(DAY_ONE, "-5.89"), ledger(DAY_TWO, "-5.90"));

    var findings = check();

    assertThat(findings).hasSize(1);
    assertThat(findings.getFirst().deviationAmount()).isEqualByComparingTo("8.00");
    assertThat(findings.getFirst().message()).contains(DAY_ONE.toString(), DAY_TWO.toString());
  }

  @Test
  void anAccrualWithNoLedgerEntryPassesWhenTheFundIsNotChargedTheFee() {
    givenChargedToFund(false);
    givenAccruals(accrual(DAY_ONE, "5.89"), accrual(DAY_TWO, "5.90"));
    givenLedger();

    assertThat(check()).singleElement().extracting(FeeCheckFinding::severity).isEqualTo(PASS);
  }

  @Test
  void aLedgerEntryOnADayTheFundIsNotChargedTheFeeStillFails() {
    givenChargedToFund(false);
    givenAccruals(accrual(DAY_ONE, "5.89"));
    givenLedger(ledger(DAY_ONE, "-5.89"));

    var finding = check().getFirst();

    assertThat(finding.severity()).isEqualTo(FAIL);
    assertThat(finding.deviationAmount()).isEqualByComparingTo("5.89");
  }

  private List<FeeCheckFinding> check() {
    return checker.check(TUK75, MANAGEMENT, DAY_ONE, DAY_TWO);
  }

  private void givenAccruals(DailyAccrualAmount... accruals) {
    given(feeAccrualRepository.findRoundedDailyGrossBetween(TUK75, MANAGEMENT, DAY_ONE, DAY_TWO))
        .willReturn(List.of(accruals));
  }

  private void givenLedger(LedgerEntryAmount... entries) {
    given(
            navLedgerRepository.findEntriesByTransactionTypeBetween(
                eq("MANAGEMENT_FEE_ACCRUAL:TUK75"), eq(FEE_ACCRUAL), any(), any()))
        .willReturn(List.of(entries));
  }

  private DailyAccrualAmount accrual(LocalDate date, String amount) {
    return new DailyAccrualAmount(date, new BigDecimal(amount));
  }

  // A late-evening UTC instant is already the next calendar day in Tallinn. Grouping on the raw
  // instant would file it under the previous day and report both days as mismatched.
  @Test
  void aLedgerEntryIsFiledUnderItsTallinnCalendarDayNotItsUtcOne() {
    givenAccruals(accrual(DAY_TWO, "5.90"));
    givenLedger(
        new LedgerEntryAmount(
            UUID.randomUUID(), Instant.parse("2026-06-02T22:30:00Z"), new BigDecimal("-5.90")));

    assertThat(check()).singleElement().extracting(FeeCheckFinding::severity).isEqualTo(PASS);
  }

  private LedgerEntryAmount ledger(LocalDate date, String amount) {
    return new LedgerEntryAmount(
        UUID.randomUUID(),
        date.atTime(9, 0).atZone(ZoneId.of("Europe/Tallinn")).toInstant(),
        new BigDecimal(amount));
  }
}
