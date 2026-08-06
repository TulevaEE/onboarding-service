package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.NOT_RUN;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.PASS;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.WARNING;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.FEE_SETTLEMENT;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.MANAGEMENT_FEE_PAYMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.ledger.LedgerEntryAmount;
import ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CashSettlementCheckerTest {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");
  private static final LocalDate FEE_MONTH = LocalDate.of(2026, 4, 1);
  private static final LocalDate WINDOW_ELAPSED = LocalDate.of(2026, 6, 3);
  private static final LocalDate WINDOW_STILL_OPEN = LocalDate.of(2026, 5, 10);
  private static final BigDecimal SETTLED = new BigDecimal("1234.56");

  @Mock private NavLedgerRepository navLedgerRepository;

  private CashSettlementChecker checker;

  @BeforeEach
  void setUp() {
    checker =
        new CashSettlementChecker(
            navLedgerRepository, new FeeCashIngestionCoverage(), new BigDecimal("0.02"), 20);
  }

  @Test
  void aFundWithoutBankStatementIngestionIsNotRunAndNeverWarns() {
    var findings = checker.check(TUK75, FEE_MONTH, WINDOW_ELAPSED);

    assertThat(findings).singleElement().extracting(FeeCheckFinding::severity).isEqualTo(NOT_RUN);
  }

  @Test
  void aPaymentMatchingTheSettlementPasses() {
    givenSettled(SETTLED);
    givenPayments(SETTLED);

    assertThat(check(WINDOW_ELAPSED).getFirst().severity()).isEqualTo(PASS);
  }

  @Test
  void aPaymentWithinToleranceStillPasses() {
    givenSettled(SETTLED);
    givenPayments(SETTLED.add(new BigDecimal("0.02")));

    assertThat(check(WINDOW_ELAPSED).getFirst().severity()).isEqualTo(PASS);
  }

  @Test
  void aPaymentDifferingBeyondToleranceIsReported() {
    givenSettled(SETTLED);
    givenPayments(SETTLED.subtract(new BigDecimal("50.00")));

    var finding = check(WINDOW_ELAPSED).getFirst();

    assertThat(finding.severity()).isEqualTo(WARNING);
    assertThat(finding.deviationAmount()).isEqualByComparingTo(new BigDecimal("50.00"));
  }

  @Test
  void noPaymentOnceTheWindowHasElapsedIsReported() {
    givenSettled(SETTLED);
    givenPayments();

    assertThat(check(WINDOW_ELAPSED).getFirst().severity()).isEqualTo(WARNING);
  }

  @Test
  void noPaymentWhileTheWindowIsStillOpenIsNotRun() {
    givenSettled(SETTLED);
    givenPayments();

    var finding = check(WINDOW_STILL_OPEN).getFirst();

    assertThat(finding.severity()).isEqualTo(NOT_RUN);
    assertThat(finding.deviationAmount()).isNull();
  }

  // The ledger carries no fee month on a payment, so a second payment in the window cannot be
  // attributed. Summing them would invent a match that the data does not support.
  @Test
  void twoPaymentsInTheWindowAreReportedEvenWhenTheySumToTheSettledAmount() {
    givenSettled(SETTLED);
    givenPayments(new BigDecimal("600.00"), new BigDecimal("634.56"));

    var finding = check(WINDOW_ELAPSED).getFirst();

    assertThat(finding.severity()).isEqualTo(WARNING);
    assertThat(finding.message()).contains("600.00", "634.56");
  }

  @Test
  void aMonthThatSettledNothingAndPaidNothingPasses() {
    givenSettled(BigDecimal.ZERO);
    givenPayments();

    assertThat(check(WINDOW_ELAPSED).getFirst().severity()).isEqualTo(PASS);
  }

  private List<FeeCheckFinding> check(LocalDate checkDate) {
    return checker.check(TKF100, FEE_MONTH, checkDate);
  }

  private void givenSettled(BigDecimal amount) {
    givenEntries(FEE_SETTLEMENT, amount);
  }

  private void givenPayments(BigDecimal... amounts) {
    givenEntries(MANAGEMENT_FEE_PAYMENT, amounts);
  }

  private void givenEntries(TransactionType transactionType, BigDecimal... amounts) {
    var entries =
        Arrays.stream(amounts)
            .filter(amount -> amount.signum() != 0)
            .map(amount -> new LedgerEntryAmount(UUID.randomUUID(), someInstant(), amount))
            .toList();
    given(
            navLedgerRepository.findEntriesByTransactionTypeBetween(
                anyString(), eq(transactionType), any(), any()))
        .willReturn(entries);
  }

  private Instant someInstant() {
    return FEE_MONTH.atStartOfDay(ESTONIAN_ZONE).toInstant();
  }
}
