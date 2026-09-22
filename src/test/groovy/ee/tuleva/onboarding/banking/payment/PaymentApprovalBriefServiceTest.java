package ee.tuleva.onboarding.banking.payment;

import static ee.tuleva.onboarding.banking.BankAccountType.WITHDRAWAL_EUR;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.ATTEMPTED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.EXECUTED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.SUBMITTED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentType.PAYOUT;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentType.REDEMPTION_TRANSFER;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentType.RETURN;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentType.SUBSCRIPTION_TRANSFER;
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
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
  private static final UUID BATCH = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID OTHER_BATCH = UUID.fromString("22222222-2222-2222-2222-222222222222");
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
        batched(SUBMITTED, REDEMPTION_TRANSFER, "100.00", BATCH),
        batched(SUBMITTED, PAYOUT, "100.00", BATCH));

    var brief = service().build(DATE, List.of());

    assertThat(brief.attention()).isFalse();
  }

  // The one number a signatory can check in their head. The transfer exists only to fund the
  // payouts, so what leaves the fund account must equal what leaves the withdrawal account.
  @Test
  void theTransferIsShownAgainstThePayoutsItFunds() {
    givenAccountResolves();
    givenPayments(
        batched(SUBMITTED, REDEMPTION_TRANSFER, "400.00", BATCH),
        batched(SUBMITTED, PAYOUT, "250.00", BATCH),
        batched(SUBMITTED, PAYOUT, "150.00", BATCH));

    var brief = service().build(DATE, List.of());

    assertThat(brief.verdicts()).contains(tie(BATCH, true, "400.00 = 400.00"));
    assertThat(brief.attention()).isFalse();
  }

  @Test
  void aTransferThatDoesNotFundItsPayoutsFailsTheVerdictAndNeedsAttention() {
    givenAccountResolves();
    givenPayments(
        batched(SUBMITTED, REDEMPTION_TRANSFER, "400.00", BATCH),
        batched(SUBMITTED, PAYOUT, "100.00", BATCH));

    var brief = service().build(DATE, List.of());

    assertThat(brief.verdicts()).contains(tie(BATCH, false, "100.00 = 400.00"));
    assertThat(brief.attention()).isTrue();
  }

  // An already approved transfer drops off the pending screen while its payouts are still on it, so
  // the tie is computed over the whole batch rather than over what is still pending. Otherwise it
  // would report an imbalance every time the signatory approved one account before the other.
  @Test
  void theTieHoldsEvenOnceOneSideHasAlreadyBeenApproved() {
    givenAccountResolves();
    givenPayments(
        batched(EXECUTED, REDEMPTION_TRANSFER, "400.00", BATCH),
        batched(SUBMITTED, PAYOUT, "400.00", BATCH));

    var brief = service().build(DATE, List.of());

    assertThat(brief.verdicts()).contains(tie(BATCH, true, "400.00 = 400.00"));
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

  // Yesterday's transfer funds yesterday's payouts, so a batch left unapproved overnight is tied
  // against its own transfer and not against whatever else is on today's brief.
  @Test
  void aBatchCarriedOverFromYesterdayIsTiedOnItsOwn() {
    givenAccountResolves();
    givenPayments(
        batched(EXECUTED, REDEMPTION_TRANSFER, "400.00", YESTERDAY_AFTERNOON, BATCH),
        batched(SUBMITTED, PAYOUT, "400.00", YESTERDAY_AFTERNOON, BATCH),
        payment(SUBMITTED, PAYOUT, "70.00"));

    var brief = service().build(DATE, List.of());

    assertThat(brief.verdicts()).contains(tie(BATCH, true, "400.00 = 400.00"));
  }

  // Each batch funds itself, so summing them would let an underfunded batch hide behind an
  // overfunded one and would make a batch stuck for weeks drag weeks of payouts into the sum.
  @Test
  void twoBatchesAreTiedIndependentlyAndOneFailingDoesNotHideTheOther() {
    givenAccountResolves();
    givenPayments(
        batched(EXECUTED, REDEMPTION_TRANSFER, "400.00", YESTERDAY_AFTERNOON, BATCH),
        batched(SUBMITTED, PAYOUT, "400.00", YESTERDAY_AFTERNOON, BATCH),
        batched(SUBMITTED, REDEMPTION_TRANSFER, "100.00", OTHER_BATCH),
        batched(SUBMITTED, PAYOUT, "60.00", OTHER_BATCH));

    var brief = service().build(DATE, List.of());

    assertThat(brief.verdicts())
        .contains(tie(BATCH, true, "400.00 = 400.00"), tie(OTHER_BATCH, false, "60.00 = 100.00"));
    assertThat(brief.attention()).isTrue();
  }

  // A retried payout is re-sent on its own, without a transfer of its own, so it has no batch to be
  // tied against. It is listed rather than silently dropped, and it does not fail its batch's tie.
  @Test
  void aRetriedPayoutWithNoBatchDoesNotBreakTheTie() {
    givenAccountResolves();
    givenPayments(
        batched(SUBMITTED, REDEMPTION_TRANSFER, "400.00", BATCH),
        batched(SUBMITTED, PAYOUT, "400.00", BATCH),
        payment(SUBMITTED, PAYOUT, "70.00"));

    var brief = service().build(DATE, List.of());

    assertThat(brief.verdicts())
        .contains(
            tie(BATCH, true, "400.00 = 400.00"),
            new PaymentApprovalBrief.Verdict(
                "retried payouts, outside any batch", true, "1 payment, 70.00"));
    assertThat(brief.attention()).isFalse();
  }

  // A batch of subscription transfers has neither a redemption transfer nor a payout, so tying it
  // would print "0.00 = 0.00" instead of a check.
  @Test
  void aBatchWithNoRedemptionTrafficShowsNoTie() {
    givenAccountResolves();
    givenPayments(batched(SUBMITTED, SUBSCRIPTION_TRANSFER, "100.00", BATCH));

    var brief = service().build(DATE, List.of());

    assertThat(brief.verdicts())
        .noneSatisfy(verdict -> assertThat(verdict.label()).contains("transfer"));
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
    lenient()
        .when(outgoingPaymentRepository.findByBatchIdIn(any()))
        .thenAnswer(invocation -> inBatches(all, invocation.getArgument(0)));
  }

  private static List<OutgoingPayment> inBatches(
      List<OutgoingPayment> payments, Collection<UUID> batchIds) {
    return payments.stream().filter(payment -> batchIds.contains(payment.getBatchId())).toList();
  }

  private static PaymentApprovalBrief.Verdict tie(UUID batchId, boolean passed, String detail) {
    return new PaymentApprovalBrief.Verdict(
        "payouts == transfer to withdrawal account (batch %s)"
            .formatted(batchId.toString().substring(0, 8)),
        passed,
        detail);
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
    return builder(status, type, amount, attemptedAt).build();
  }

  private static OutgoingPayment batched(
      OutgoingPaymentStatus status, OutgoingPaymentType type, String amount, UUID batchId) {
    return batched(status, type, amount, TODAY_AFTERNOON, batchId);
  }

  private static OutgoingPayment batched(
      OutgoingPaymentStatus status,
      OutgoingPaymentType type,
      String amount,
      Instant attemptedAt,
      UUID batchId) {
    return builder(status, type, amount, attemptedAt).batchId(batchId).build();
  }

  private static OutgoingPayment.OutgoingPaymentBuilder builder(
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
        .attemptedAt(attemptedAt);
  }

  @Test
  void aFindingAboutMoneyThatAlreadyLeftIsNotCountedAsHeldBackFromTheBank() {
    givenAccountResolves();
    givenPayments(
        batched(SUBMITTED, REDEMPTION_TRANSFER, "100.00", BATCH),
        batched(SUBMITTED, PAYOUT, "100.00", BATCH));

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
