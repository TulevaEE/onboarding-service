package ee.tuleva.onboarding.banking.seb.processor;

import static ee.tuleva.onboarding.ledger.SystemAccount.MANAGEMENT_FEE;
import static ee.tuleva.onboarding.ledger.SystemAccount.UNDERLYING_FUND_MANAGEMENT_FEE;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static java.math.BigDecimal.ZERO;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ee.tuleva.onboarding.banking.seb.SebIntegrationTest;
import ee.tuleva.onboarding.ledger.BookedManagementFeePayment;
import ee.tuleva.onboarding.ledger.FundBankLedger;
import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.ledger.SystemAccount;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@SebIntegrationTest
class UnderlyingFundFeeMoveServiceTest {

  private static final UUID APRIL_BR_PAYMENT = randomUUID();
  private static final UUID MAY_BR_PAYMENT = randomUUID();

  @Autowired private UnderlyingFundFeeMoveService service;
  @Autowired private FundBankLedger fundBankLedger;
  @Autowired private LedgerService ledgerService;

  @Test
  void preview_countsAndSumsTheBrTasudPayments_andWritesNothing() {
    bookAprilAndMayBrPaymentsBesideAManagementFeePayment();

    var plan = service.preview(TUV100);

    assertThat(plan.count()).isEqualTo(2);
    assertThat(plan.sum()).isEqualByComparingTo(new BigDecimal("75.50"));
    assertThat(plan.alreadyMoved()).isZero();
    assertThat(plan.payments())
        .extracting(
            BookedManagementFeePayment::externalReference, BookedManagementFeePayment::bookingDate)
        .containsExactly(
            tuple(APRIL_BR_PAYMENT, LocalDate.of(2026, 4, 15)),
            tuple(MAY_BR_PAYMENT, LocalDate.of(2026, 5, 12)));
    assertThat(balance(MANAGEMENT_FEE)).isEqualByComparingTo(new BigDecimal("817.84"));
    assertThat(balance(UNDERLYING_FUND_MANAGEMENT_FEE)).isEqualByComparingTo(ZERO);
  }

  @Test
  void move_movesTheBrTasudPayments_andLeavesTheManagementFeePaymentOnTheManagementFee() {
    bookAprilAndMayBrPaymentsBesideAManagementFeePayment();

    var plan = service.move(TUV100);

    assertThat(plan.count()).isEqualTo(2);
    assertThat(plan.sum()).isEqualByComparingTo(new BigDecimal("75.50"));
    assertThat(balance(MANAGEMENT_FEE)).isEqualByComparingTo(new BigDecimal("742.34"));
    assertThat(balance(UNDERLYING_FUND_MANAGEMENT_FEE))
        .isEqualByComparingTo(new BigDecimal("75.50"));
  }

  @Test
  void move_runAgain_movesNothing_andCountsThePaymentsAsAlreadyMoved() {
    bookAprilAndMayBrPaymentsBesideAManagementFeePayment();
    service.move(TUV100);

    var secondRun = service.move(TUV100);

    assertThat(secondRun.count()).isZero();
    assertThat(secondRun.sum()).isEqualByComparingTo(ZERO);
    assertThat(secondRun.alreadyMoved()).isEqualTo(2);
    assertThat(service.preview(TUV100).count()).isZero();
    assertThat(balance(MANAGEMENT_FEE)).isEqualByComparingTo(new BigDecimal("742.34"));
    assertThat(balance(UNDERLYING_FUND_MANAGEMENT_FEE))
        .isEqualByComparingTo(new BigDecimal("75.50"));
  }

  private void bookAprilAndMayBrPaymentsBesideAManagementFeePayment() {
    fundBankLedger.recordManagementFeePayment(
        TUV100,
        new BigDecimal("742.34"),
        randomUUID(),
        "TUV100 valitsemistasu 03/2026",
        LocalDate.of(2026, 4, 10));
    fundBankLedger.recordManagementFeePayment(
        TUV100,
        new BigDecimal("50.00"),
        APRIL_BR_PAYMENT,
        "BR tasud TUV100",
        LocalDate.of(2026, 4, 15));
    fundBankLedger.recordManagementFeePayment(
        TUV100,
        new BigDecimal("25.50"),
        MAY_BR_PAYMENT,
        "TUV100  BR TASUD 05/2026",
        LocalDate.of(2026, 5, 12));
  }

  private BigDecimal balance(SystemAccount systemAccount) {
    return ledgerService.getSystemAccount(systemAccount, TUV100).getBalance();
  }
}
