package ee.tuleva.onboarding.banking.payment;

import static ee.tuleva.onboarding.banking.BankAccountType.WITHDRAWAL_EUR;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.ATTEMPTED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.EXECUTED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.SUBMITTED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentType.PAYOUT;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentType.REDEMPTION_TRANSFER;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentType.RETURN;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.seb.SebAccountBalanceReader;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentApprovalBriefServiceTest {

  private static final LocalDate DATE = LocalDate.of(2026, 4, 10);
  private static final Instant TODAY_AFTERNOON = Instant.parse("2026-04-10T13:05:00Z");
  private static final Instant YESTERDAY_AFTERNOON = Instant.parse("2026-04-09T13:05:00Z");
  private static final String IBAN = "EE222222222222222222";
  private static final BankAccount ACCOUNT =
      new BankAccount(IBAN, WITHDRAWAL_EUR, TKF100, "gateway-client");

  @Mock OutgoingPaymentRepository outgoingPaymentRepository;
  @Mock BankAccounts bankAccounts;
  @Mock SebAccountBalanceReader balanceReader;

  private PaymentApprovalBriefService service() {
    return new PaymentApprovalBriefService(outgoingPaymentRepository, bankAccounts, balanceReader);
  }

  // A payment already executed was approved earlier and is no longer on the bank's pending screen,
  // so counting it would make the brief disagree with what the signatory is looking at.
  @Test
  void anAlreadyExecutedPaymentIsNotOnThePendingScreenSoItIsNotCounted() {
    givenAccountResolves();
    givenPayments(payment(SUBMITTED, PAYOUT, "100.00"), payment(EXECUTED, PAYOUT, "999.00"));

    var brief = service().build(DATE, List.of());

    assertThat(brief.totalPaymentCount()).isEqualTo(1);
    assertThat(brief.grandTotal()).isEqualByComparingTo("100.00");
  }

  @Test
  void theProjectedBalanceIsWhatTheAccountIsLeftWithOnceThesePaymentsExecute() {
    givenAccountResolves();
    given(balanceReader.available(ACCOUNT)).willReturn(Optional.of(new BigDecimal("1000.00")));
    givenPayments(payment(SUBMITTED, PAYOUT, "300.00"));

    var brief = service().build(DATE, List.of());

    assertThat(brief.accounts())
        .singleElement()
        .satisfies(
            account -> assertThat(account.projectedBalance()).isEqualByComparingTo("700.00"));
  }

  @Test
  void anAccountThatWouldGoNegativeNeedsAttention() {
    givenAccountResolves();
    given(balanceReader.available(ACCOUNT)).willReturn(Optional.of(new BigDecimal("100.00")));
    givenPayments(payment(SUBMITTED, PAYOUT, "300.00"));

    var brief = service().build(DATE, List.of());

    assertThat(brief.accounts())
        .singleElement()
        .satisfies(account -> assertThat(account.goesNegative()).isTrue());
    assertThat(brief.attention()).isTrue();
  }

  // In flight means the call never returned a verdict: the payment may or may not have reached the
  // bank, which is exactly the kind of day that deserves a proper look.
  @Test
  void aPaymentStillInFlightNeedsAttention() {
    givenAccountResolves();
    givenPayments(payment(ATTEMPTED, PAYOUT, "100.00"));

    var brief = service().build(DATE, List.of());

    assertThat(brief.attention()).isTrue();
  }

  @Test
  void aCleanDayWithNothingHeldNeedsNoAttention() {
    givenAccountResolves();
    givenPayments(
        payment(SUBMITTED, REDEMPTION_TRANSFER, "100.00"), payment(SUBMITTED, PAYOUT, "100.00"));

    var brief = service().build(DATE, List.of());

    assertThat(brief.attention()).isFalse();
  }

  // The one number a signatory can check in their head. The transfer exists only to fund the
  // payouts, so what leaves the fund account must equal what leaves the withdrawal account.
  @Test
  void theTransferIsShownAgainstThePayoutsItFunds() {
    givenAccountResolves();
    givenPayments(
        payment(SUBMITTED, REDEMPTION_TRANSFER, "400.00"),
        payment(SUBMITTED, PAYOUT, "250.00"),
        payment(SUBMITTED, PAYOUT, "150.00"));

    var brief = service().build(DATE, List.of());

    assertThat(brief.verdicts())
        .contains(
            new PaymentApprovalBrief.Verdict(
                "payouts == transfer to withdrawal account", true, "400.00 = 400.00"));
    assertThat(brief.attention()).isFalse();
  }

  @Test
  void aTransferThatDoesNotFundItsPayoutsFailsTheVerdictAndNeedsAttention() {
    givenAccountResolves();
    givenPayments(
        payment(SUBMITTED, REDEMPTION_TRANSFER, "400.00"), payment(SUBMITTED, PAYOUT, "100.00"));

    var brief = service().build(DATE, List.of());

    assertThat(brief.verdicts())
        .contains(
            new PaymentApprovalBrief.Verdict(
                "payouts == transfer to withdrawal account", false, "100.00 = 400.00"));
    assertThat(brief.attention()).isTrue();
  }

  // An already approved transfer drops off the pending screen while its payouts are still on it, so
  // the tie is computed over the whole day rather than over what is still pending. Otherwise it
  // would report an imbalance every time the signatory approved one account before the other.
  @Test
  void theTieHoldsEvenOnceOneSideHasAlreadyBeenApproved() {
    givenAccountResolves();
    givenPayments(
        payment(EXECUTED, REDEMPTION_TRANSFER, "400.00"), payment(SUBMITTED, PAYOUT, "400.00"));

    var brief = service().build(DATE, List.of());

    assertThat(brief.verdicts())
        .contains(
            new PaymentApprovalBrief.Verdict(
                "payouts == transfer to withdrawal account", true, "400.00 = 400.00"));
  }

  // The bank keeps a payment on its pending screen until somebody approves it, so a batch created
  // after yesterday's cutoff and left unapproved overnight has to stay on the brief.
  @Test
  void aPaymentLeftUnapprovedOvernightIsStillOnTheBrief() {
    givenAccountResolves();
    givenPayments(
        payment(SUBMITTED, PAYOUT, "100.00", YESTERDAY_AFTERNOON),
        payment(SUBMITTED, PAYOUT, "50.00", TODAY_AFTERNOON));

    var brief = service().build(DATE, List.of());

    assertThat(brief.totalPaymentCount()).isEqualTo(2);
    assertThat(brief.grandTotal()).isEqualByComparingTo("150.00");
  }

  // Yesterday's transfer funds yesterday's payouts, so a payout carried into today has to be tied
  // against the day it was attempted rather than against today's empty transfer total.
  @Test
  void aCarriedOverPayoutIsTiedAgainstTheTransferOfTheDayItWasAttempted() {
    givenAccountResolves();
    givenPayments(
        payment(EXECUTED, REDEMPTION_TRANSFER, "400.00", YESTERDAY_AFTERNOON),
        payment(SUBMITTED, PAYOUT, "400.00", YESTERDAY_AFTERNOON));

    var brief = service().build(DATE, List.of());

    assertThat(brief.verdicts())
        .contains(
            new PaymentApprovalBrief.Verdict(
                "payouts == transfer to withdrawal account", true, "400.00 = 400.00"));
  }

  // A day with neither a transfer nor a payout has nothing to tie, and an equation reading
  // "0.00 = 0.00" would be noise rather than a check.
  @Test
  void aDayWithNoRedemptionTrafficShowsNoTie() {
    givenAccountResolves();
    givenPayments(payment(SUBMITTED, RETURN, "100.00"));

    var brief = service().build(DATE, List.of());

    assertThat(brief.verdicts())
        .noneSatisfy(verdict -> assertThat(verdict.label()).contains("transfer"));
  }

  // A tick has to be a statement about today rather than decoration: the gate is green because it
  // held nothing, and a gate that held something says so instead.
  @Test
  void aGateThatHeldSomethingIsNotTicked() {
    givenAccountResolves();
    givenPayments(payment(SUBMITTED, PAYOUT, "100.00"));

    var brief =
        service()
            .build(
                DATE,
                List.of(
                    new PaymentApprovalBriefService.PaymentHold(
                        "PAYOUT_BLOCKED", "iban not the party's")));

    assertThat(brief.verdicts())
        .contains(new PaymentApprovalBrief.Verdict("payout entitlement", false, "1 held"))
        .contains(
            new PaymentApprovalBrief.Verdict("file integrity (XSD + parse-back)", true, null));
  }

  @Test
  void heldPaymentsAreCountedWithTheirDistinctReasonsAndNeedAttention() {
    givenAccountResolves();
    givenPayments(payment(SUBMITTED, PAYOUT, "100.00"));

    var brief =
        service()
            .build(
                DATE,
                List.of(
                    new PaymentApprovalBriefService.PaymentHold(
                        "PAYOUT_BLOCKED", "iban not the party's"),
                    new PaymentApprovalBriefService.PaymentHold(
                        "PAYOUT_BLOCKED", "iban not the party's"),
                    new PaymentApprovalBriefService.PaymentHold(
                        "PAYMENT_BLOCKED", "name will not resolve")));

    assertThat(brief.heldCount()).isEqualTo(3);
    assertThat(brief.heldReasons())
        .containsExactlyInAnyOrder("iban not the party's", "name will not resolve");
    assertThat(brief.attention()).isTrue();
  }

  @Test
  void paymentsAreSummarisedPerFlowWithinAnAccount() {
    givenAccountResolves();
    givenPayments(
        payment(SUBMITTED, PAYOUT, "100.00"),
        payment(SUBMITTED, PAYOUT, "50.00"),
        payment(SUBMITTED, REDEMPTION_TRANSFER, "400.00"));

    var brief = service().build(DATE, List.of());

    assertThat(brief.accounts())
        .singleElement()
        .satisfies(
            account ->
                assertThat(account.flows())
                    .containsExactly(
                        new PaymentApprovalBrief.FlowSummary(
                            "payouts to clients", 2, new BigDecimal("150.00")),
                        new PaymentApprovalBrief.FlowSummary(
                            "to withdrawal account", 1, new BigDecimal("400.00"))));
  }

  private void givenAccountResolves() {
    lenient().when(bankAccounts.find(IBAN)).thenReturn(Optional.of(ACCOUNT));
    lenient().when(balanceReader.available(any(BankAccount.class))).thenReturn(Optional.empty());
  }

  private void givenPayments(OutgoingPayment... payments) {
    var all = List.of(payments);
    given(outgoingPaymentRepository.findAwaitingApproval())
        .willReturn(all.stream().filter(PaymentApprovalBriefServiceTest::awaitsApproval).toList());
    given(outgoingPaymentRepository.findByAttemptedAtBetween(any(), any()))
        .willAnswer(
            invocation ->
                attemptedBetween(all, invocation.getArgument(0), invocation.getArgument(1)));
  }

  private static boolean awaitsApproval(OutgoingPayment payment) {
    return payment.getStatus() == SUBMITTED || payment.getStatus() == ATTEMPTED;
  }

  private static List<OutgoingPayment> attemptedBetween(
      List<OutgoingPayment> payments, Instant from, Instant to) {
    return payments.stream()
        .filter(payment -> !payment.getAttemptedAt().isBefore(from))
        .filter(payment -> payment.getAttemptedAt().isBefore(to))
        .toList();
  }

  private static OutgoingPayment payment(
      OutgoingPaymentStatus status, OutgoingPaymentType type, String amount) {
    return payment(status, type, amount, TODAY_AFTERNOON);
  }

  private static OutgoingPayment payment(
      OutgoingPaymentStatus status, OutgoingPaymentType type, String amount, Instant attemptedAt) {
    return OutgoingPayment.builder()
        .endToEndId("E2E-" + amount + "-" + type + "-" + attemptedAt)
        .paymentType(type)
        .remitterIban(IBAN)
        .beneficiaryIban("EE333333333333333333")
        .amount(new BigDecimal(amount))
        .currency("EUR")
        .bodyHash("hash")
        .status(status)
        .attemptedAt(attemptedAt)
        .build();
  }

  @Test
  void aFindingAboutMoneyThatAlreadyLeftIsNotCountedAsHeldBackFromTheBank() {
    givenAccountResolves();
    givenPayments(
        payment(SUBMITTED, REDEMPTION_TRANSFER, "100.00"), payment(SUBMITTED, PAYOUT, "100.00"));

    var brief =
        service()
            .build(
                DATE,
                List.of(
                    new PaymentApprovalBriefService.PaymentHold(
                        "PHANTOM_DEBIT", "no outgoing payment was ever recorded for this debit"),
                    new PaymentApprovalBriefService.PaymentHold(
                        "DEBIT_MISMATCH", "the bank debited an amount other than we authorised")));

    assertThat(brief.heldCount()).isZero();
    assertThat(brief.heldReasons()).isEmpty();
    assertThat(brief.attention()).isFalse();
  }
}
