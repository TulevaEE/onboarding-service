package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.MANAGEMENT_FEE_PAYMENT;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.UNDERLYING_FUND_MANAGEMENT_FEE_PAYMENT;
import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_INVESTMENT_CASH_CLEARING;
import static ee.tuleva.onboarding.ledger.SystemAccount.MANAGEMENT_FEE;
import static ee.tuleva.onboarding.ledger.SystemAccount.UNDERLYING_FUND_MANAGEMENT_FEE;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.math.BigDecimal.ZERO;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.time.ClockConfig;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({
  LedgerService.class,
  LedgerAccountService.class,
  LedgerPartyService.class,
  LedgerTransactionService.class,
  UserUnitBalanceGuard.class,
  FundBankLedger.class,
  UnderlyingFundFeeMoves.class,
  ClockConfig.class
})
class UnderlyingFundFeeMovesTest {

  private static final LocalDate APRIL_PAYMENT_DAY = LocalDate.of(2026, 4, 15);
  private static final Instant END_OF_APRIL_PAYMENT_DAY_IN_TALLINN =
      Instant.parse("2026-04-15T20:59:59.999999Z");

  @Autowired LedgerService ledgerService;
  @Autowired FundBankLedger fundBankLedger;
  @Autowired UnderlyingFundFeeMoves underlyingFundFeeMoves;

  @Test
  void move_booksTheUnderlyingFundFeeOnThePaymentsOwnDate_andTakesItOffTheManagementFee() {
    var externalReference = randomUUID();
    fundBankLedger.recordManagementFeePayment(
        TUK75, new BigDecimal("50.00"), externalReference, "BR tasud TUK75", APRIL_PAYMENT_DAY);
    var payment = paymentWith(TUK75, externalReference);
    var managementFeeBefore = balance(MANAGEMENT_FEE, TUK75);
    var underlyingFeeBefore = balance(UNDERLYING_FUND_MANAGEMENT_FEE, TUK75);
    var cashBefore = balance(FUND_INVESTMENT_CASH_CLEARING, TUK75);

    var move = underlyingFundFeeMoves.move(payment);

    assertThat(payment)
        .isEqualTo(
            new BookedManagementFeePayment(
                TUK75,
                externalReference,
                END_OF_APRIL_PAYMENT_DAY_IN_TALLINN,
                new BigDecimal("50.00"),
                "BR tasud TUK75"));
    assertThat(payment.bookingDate()).isEqualTo(APRIL_PAYMENT_DAY);
    assertThat(move.getTransactionType()).isEqualTo(UNDERLYING_FUND_MANAGEMENT_FEE_PAYMENT);
    assertThat(move.getTransactionDate()).isEqualTo(END_OF_APRIL_PAYMENT_DAY_IN_TALLINN);
    assertThat(move.getExternalReference()).isEqualTo(externalReference);
    assertThat(move.getMetadata())
        .containsEntry("operationType", "UNDERLYING_FUND_MANAGEMENT_FEE_PAYMENT")
        .containsEntry("description", "Moved from the management fee: BR tasud TUK75");
    assertThat(move.sum()).isEqualByComparingTo(ZERO);
    assertThat(balance(MANAGEMENT_FEE, TUK75).subtract(managementFeeBefore))
        .isEqualByComparingTo(new BigDecimal("-50.00"));
    assertThat(balance(UNDERLYING_FUND_MANAGEMENT_FEE, TUK75).subtract(underlyingFeeBefore))
        .isEqualByComparingTo(new BigDecimal("50.00"));
    assertThat(balance(FUND_INVESTMENT_CASH_CLEARING, TUK75)).isEqualByComparingTo(cashBefore);
  }

  @Test
  void move_ofAPaymentAlreadyMoved_returnsTheEarlierMoveAndBooksNothing() {
    var externalReference = randomUUID();
    fundBankLedger.recordManagementFeePayment(
        TUK75, new BigDecimal("50.00"), externalReference, "BR tasud TUK75", APRIL_PAYMENT_DAY);
    var payment = paymentWith(TUK75, externalReference);
    assertThat(underlyingFundFeeMoves.isMoved(payment)).isFalse();
    var firstMove = underlyingFundFeeMoves.move(payment);
    var managementFeeAfterFirstMove = balance(MANAGEMENT_FEE, TUK75);

    var secondMove = underlyingFundFeeMoves.move(payment);

    assertThat(underlyingFundFeeMoves.isMoved(payment)).isTrue();
    assertThat(secondMove.getId()).isEqualTo(firstMove.getId());
    assertThat(balance(MANAGEMENT_FEE, TUK75)).isEqualByComparingTo(managementFeeAfterFirstMove);
  }

  @Test
  void findManagementFeePayments_readsAReclassifiedPaymentsBankTextFromItsSuspenseEntry() {
    var externalReference = randomUUID();
    fundBankLedger.recordUnclassifiedBankEntry(
        TUK00,
        new BigDecimal("-30.00"),
        externalReference,
        FUND_INVESTMENT_CASH_CLEARING,
        APRIL_PAYMENT_DAY,
        new UnclassifiedEntryDetails(
            "Tuleva Fondid AS", "EE001234567890123488", "BR tasud TUK00", "ICDT"));
    fundBankLedger.reclassifySuspenseEntry(
        TUK00,
        new BigDecimal("-30.00"),
        externalReference,
        MANAGEMENT_FEE_PAYMENT,
        APRIL_PAYMENT_DAY);

    assertThat(paymentWith(TUK00, externalReference))
        .isEqualTo(
            new BookedManagementFeePayment(
                TUK00,
                externalReference,
                END_OF_APRIL_PAYMENT_DAY_IN_TALLINN,
                new BigDecimal("30.00"),
                "BR tasud TUK00"));
  }

  @Test
  void findManagementFeePayments_leavesOutAnotherFundsPayments() {
    var externalReference = randomUUID();
    fundBankLedger.recordManagementFeePayment(
        TUK75, new BigDecimal("50.00"), externalReference, "BR tasud TUK75", APRIL_PAYMENT_DAY);

    assertThat(underlyingFundFeeMoves.findManagementFeePayments(TUK00))
        .extracting(BookedManagementFeePayment::externalReference)
        .doesNotContain(externalReference);
  }

  private BookedManagementFeePayment paymentWith(TulevaFund fund, UUID externalReference) {
    return underlyingFundFeeMoves.findManagementFeePayments(fund).stream()
        .filter(payment -> payment.externalReference().equals(externalReference))
        .findFirst()
        .orElseThrow();
  }

  private BigDecimal balance(SystemAccount systemAccount, TulevaFund fund) {
    return ledgerService.getSystemAccount(systemAccount, fund).getBalance();
  }
}
