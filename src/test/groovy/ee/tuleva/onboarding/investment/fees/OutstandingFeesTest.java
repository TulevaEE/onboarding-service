package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.investment.config.InvestmentParameter.MANAGEMENT_FEE_PAYMENT_THRESHOLD;
import static ee.tuleva.onboarding.investment.fees.FeeType.DEPOT;
import static ee.tuleva.onboarding.investment.fees.FeeType.MANAGEMENT;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.MANAGEMENT_FEE_PAYMENT;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.math.BigDecimal.ZERO;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import ee.tuleva.onboarding.investment.fees.FeeChargedToFundPolicy.Policy;
import ee.tuleva.onboarding.investment.fees.FeeChargedToFundPolicy.Resolver;
import ee.tuleva.onboarding.ledger.LedgerEntryAmount;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
class OutstandingFeesTest {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");
  private static final LocalDate FUND_START = LocalDate.of(2017, 3, 28);
  private static final LocalDate FIRST_MONTH_SETTLED_BY_PAYMENT = LocalDate.of(2026, 9, 1);
  private static final LocalDate SEPTEMBER_30 = LocalDate.of(2026, 9, 30);
  private static final LocalDate OCTOBER_1 = LocalDate.of(2026, 10, 1);
  private static final LocalDate OCTOBER_2 = LocalDate.of(2026, 10, 2);
  private static final LocalDate OCTOBER_3 = LocalDate.of(2026, 10, 3);

  @Mock private FeeAccrualRepository feeAccrualRepository;
  @Mock private NavLedgerRepository navLedgerRepository;
  @Mock private InvestmentParameterRepository investmentParameterRepository;

  private OutstandingFees outstandingFees;

  @BeforeEach
  void setUp() {
    outstandingFees =
        new OutstandingFees(
            feeAccrualRepository,
            navLedgerRepository,
            investmentParameterRepository,
            FIRST_MONTH_SETTLED_BY_PAYMENT);
  }

  @Test
  void owesTheMonthsChargedAccrualsWithAnExactHalfCentRoundedUp() {
    givenAccruals(OCTOBER_3, Map.of(OCTOBER_1, "100.002", OCTOBER_2, "200.003"));

    assertThat(outstandingFees.owedOn(charged(MANAGEMENT), OCTOBER_3)).isEqualTo("300.01");
  }

  @Test
  void owesTheMonthsChargedAccrualsWithLessThanHalfACentRoundedDown() {
    givenAccruals(OCTOBER_3, Map.of(OCTOBER_1, "100.001", OCTOBER_2, "200.003"));

    assertThat(outstandingFees.owedOn(charged(MANAGEMENT), OCTOBER_3)).isEqualTo("300.00");
  }

  @Test
  void keepsLastMonthsFeeOwedAfterTheMonthEndsWhileNoDebitHasPaidIt() {
    givenAccruals(
        OCTOBER_2, Map.of(SEPTEMBER_30, "100.004", OCTOBER_1, "50.001", OCTOBER_2, "50.002"));

    assertThat(outstandingFees.owedOn(charged(MANAGEMENT), OCTOBER_2)).isEqualTo("200.00");
  }

  @Test
  void aDebitBookedByTheDateClearsTheMonthItPays() {
    givenAccruals(
        OCTOBER_2, Map.of(SEPTEMBER_30, "100.004", OCTOBER_1, "50.001", OCTOBER_2, "50.002"));
    givenThreshold(OCTOBER_1, "0.02");
    givenDebits(OCTOBER_2, debit(OCTOBER_1, "100.00"));

    var months = outstandingFees.monthsOn(charged(MANAGEMENT), OCTOBER_2);

    assertThat(months)
        .containsExactly(
            new FeeMonthBalance(
                LocalDate.of(2026, 9, 1),
                new BigDecimal("100.00"),
                new BigDecimal("100.00"),
                ZERO,
                OCTOBER_1,
                List.of(new FeeMonthPayment(OCTOBER_1, ZERO))),
            unpaid("2026-10-01", "100.00"));
    assertThat(OutstandingFees.owed(months)).isEqualTo("100.00");
  }

  @Test
  void closesAMonthWithinTheManagementFeeThresholdSetInTheParameters() {
    givenAccruals(OCTOBER_2, Map.of(SEPTEMBER_30, "100.00", OCTOBER_1, "50.00"));
    givenThreshold(OCTOBER_1, "0.05");
    givenDebits(OCTOBER_2, debit(OCTOBER_1, "99.96"));

    assertThat(outstandingFees.owedOn(charged(MANAGEMENT), OCTOBER_2)).isEqualTo("50.00");
  }

  @Test
  void aThresholdRaisedAfterADebitWasBookedDoesNotClearTheMonthItLeftShort() {
    givenAccruals(OCTOBER_3, Map.of(SEPTEMBER_30, "100.00", OCTOBER_1, "50.00"));
    givenThreshold(OCTOBER_1, "0.02");
    lenient()
        .when(
            investmentParameterRepository.findLatestValue(
                MANAGEMENT_FEE_PAYMENT_THRESHOLD, OCTOBER_3))
        .thenReturn(new BigDecimal("0.05"));
    givenDebits(OCTOBER_3, debit(OCTOBER_1, "99.96"));

    assertThat(outstandingFees.owedOn(charged(MANAGEMENT), OCTOBER_3)).isEqualTo("50.04");
  }

  @Test
  void owesOnlyTheCurrentMonthBeforeTheFirstMonthSettledByPayment() {
    var january15 = LocalDate.of(2025, 1, 15);
    given(
            feeAccrualRepository.getAccrualByDateBetween(
                TUK75, MANAGEMENT, LocalDate.of(2025, 1, 1), january15))
        .willReturn(Map.of(january15, new BigDecimal("10.004")));

    assertThat(outstandingFees.owedOn(charged(MANAGEMENT), january15)).isEqualTo("10.00");
    verifyNoInteractions(navLedgerRepository, investmentParameterRepository);
  }

  @Test
  void owesNothingForAFeeTulevaPaysItself() {
    given(feeAccrualRepository.getAccrualByDateBetween(TUK75, DEPOT, OCTOBER_1, OCTOBER_3))
        .willReturn(Map.of(OCTOBER_1, new BigDecimal("12.34")));

    assertThat(outstandingFees.owedOn(notCharged(DEPOT), OCTOBER_3)).isEqualByComparingTo(ZERO);
    verifyNoInteractions(navLedgerRepository, investmentParameterRepository);
  }

  @Test
  void owesNothingBeforeTheMonthsFirstAccrual() {
    givenAccruals(OCTOBER_3, Map.of());

    assertThat(outstandingFees.owedOn(charged(MANAGEMENT), OCTOBER_3)).isEqualTo(ZERO);
  }

  @Test
  void releasesTheManagementFeeAtMonthEndOnlyBeforeTheFirstMonthSettledByPayment() {
    assertThat(outstandingFees.releasedAtMonthEnd(MANAGEMENT, LocalDate.of(2026, 8, 1))).isTrue();
    assertThat(outstandingFees.releasedAtMonthEnd(MANAGEMENT, FIRST_MONTH_SETTLED_BY_PAYMENT))
        .isFalse();
  }

  @Test
  void releasesTheDepotFeeAtMonthEndBecauseNoDebitClearsIt() {
    assertThat(outstandingFees.releasedAtMonthEnd(DEPOT, OCTOBER_1)).isTrue();
  }

  private void givenAccruals(LocalDate date, Map<LocalDate, String> amounts) {
    given(
            feeAccrualRepository.getAccrualByDateBetween(
                TUK75, MANAGEMENT, FIRST_MONTH_SETTLED_BY_PAYMENT, date))
        .willReturn(
            amounts.entrySet().stream()
                .collect(toMap(Map.Entry::getKey, entry -> new BigDecimal(entry.getValue()))));
  }

  private void givenThreshold(LocalDate date, String threshold) {
    given(investmentParameterRepository.findLatestValue(MANAGEMENT_FEE_PAYMENT_THRESHOLD, date))
        .willReturn(new BigDecimal(threshold));
  }

  private void givenDebits(LocalDate date, LedgerEntryAmount... debits) {
    given(
            navLedgerRepository.findEntriesByTransactionTypeBetween(
                "MANAGEMENT_FEE:TUK75",
                MANAGEMENT_FEE_PAYMENT,
                startOf(FIRST_MONTH_SETTLED_BY_PAYMENT.plusMonths(1)),
                startOf(date.plusDays(1))))
        .willReturn(List.of(debits));
  }

  private static LedgerEntryAmount debit(LocalDate bookedOn, String amount) {
    return new LedgerEntryAmount(
        UUID.randomUUID(),
        bookedOn.atTime(23, 59, 59).atZone(ESTONIAN_ZONE).toInstant(),
        new BigDecimal(amount));
  }

  private static Instant startOf(LocalDate date) {
    return date.atStartOfDay(ESTONIAN_ZONE).toInstant();
  }

  private static FeeMonthBalance unpaid(String feeMonth, String accrued) {
    return new FeeMonthBalance(
        LocalDate.parse(feeMonth),
        new BigDecimal(accrued),
        ZERO,
        new BigDecimal(accrued),
        null,
        List.of());
  }

  private static Resolver charged(FeeType feeType) {
    return new Resolver(TUK75, feeType, List.of(new Policy(true, FUND_START, null)));
  }

  private static Resolver notCharged(FeeType feeType) {
    return new Resolver(TUK75, feeType, List.of(new Policy(false, FUND_START, null)));
  }
}
