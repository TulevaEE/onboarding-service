package ee.tuleva.onboarding.banking.payment;

import static ee.tuleva.onboarding.banking.BankAccountType.DEPOSIT_EUR;
import static ee.tuleva.onboarding.banking.BankAccountType.FUND_INVESTMENT_EUR;
import static ee.tuleva.onboarding.banking.BankAccountType.WITHDRAWAL_EUR;
import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckSeverity.HOLD;
import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckType.DEBIT_MISMATCH;
import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckType.PAYMENT_BATCH_NOT_SETTLED;
import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckType.PAYMENT_BATCH_SETTLED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.ATTEMPTED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.EXECUTED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.FAILED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.SUBMITTED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentType.PAYOUT;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentType.REDEMPTION_TRANSFER;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentType.RETURN;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentType.SUBSCRIPTION_TRANSFER;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static java.math.BigDecimal.ZERO;
import static java.time.ZoneOffset.UTC;
import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.banking.BankAccountType;
import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.check.payment.PaymentCheckService;
import ee.tuleva.onboarding.banking.payment.PaymentSettlementCheck.Closing;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.time.ClockConfig;
import ee.tuleva.onboarding.time.ClockHolder;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest
@Import({
  PaymentSettlementCheck.class,
  LedgerTies.class,
  PaymentAccounts.class,
  BriefedBatches.class,
  PublicHolidays.class,
  PaymentCheckService.class,
  ClockConfig.class,
  PaymentSettlementCheckIT.Ledger.class
})
class PaymentSettlementCheckIT {

  @TestConfiguration
  static class Ledger {
    @Bean
    FakeLedgerExpectations ledgerExpectations() {
      return new FakeLedgerExpectations();
    }
  }

  static class FakeLedgerExpectations implements LedgerExpectations {
    final Map<UUID, BigDecimal> pricedRedemptions = new HashMap<>();
    final Map<UUID, BigDecimal> pricedBatches = new HashMap<>();
    final Map<UUID, BigDecimal> bookedReturns = new HashMap<>();
    BigDecimal issuedSubscriptions = ZERO;

    void clear() {
      pricedRedemptions.clear();
      pricedBatches.clear();
      bookedReturns.clear();
      issuedSubscriptions = ZERO;
    }

    @Override
    public Optional<BigDecimal> pricedRedemption(UUID redemptionRequestId) {
      return Optional.ofNullable(pricedRedemptions.get(redemptionRequestId));
    }

    @Override
    public Optional<BigDecimal> pricedRedemptionBatch(UUID batchId) {
      return Optional.ofNullable(pricedBatches.get(batchId));
    }

    @Override
    public BigDecimal issuedSubscriptions(Instant after, Instant until) {
      return after.equals(PREVIOUS_BRIEF) && until.equals(BRIEF) ? issuedSubscriptions : ZERO;
    }

    @Override
    public Optional<BigDecimal> bookedReturn(UUID paymentId) {
      return Optional.ofNullable(bookedReturns.get(paymentId));
    }
  }

  private static final LocalDate TODAY = LocalDate.of(2026, 9, 23);
  private static final Instant PREVIOUS_BRIEF = Instant.parse("2026-09-22T13:10:00Z");
  private static final Instant BRIEF = Instant.parse("2026-09-23T13:10:00Z");
  private static final Instant BEFORE_THE_BRIEF = Instant.parse("2026-09-23T13:02:00Z");
  private static final String DEPOSIT_IBAN = "EE444444444444444444";
  private static final String FUND_INVESTMENT_IBAN = "EE555555555555555555";
  private static final String WITHDRAWAL_IBAN = "EE666666666666666666";
  private static final String BENEFICIARY_IBAN = "EE777777777777777777";

  @Autowired PaymentSettlementCheck settlementCheck;
  @Autowired BriefedBatches briefedBatches;
  @Autowired PaymentCheckService paymentCheckService;
  @Autowired OutgoingPaymentRepository outgoingPaymentRepository;
  @Autowired FakeLedgerExpectations ledger;
  @MockitoBean BankAccounts bankAccounts;
  @MockitoBean OperationsNotificationService notificationService;

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @BeforeEach
  void theAccountsAreOursAndTheLedgerIsEmpty() {
    ledger.clear();
    ours(DEPOSIT_IBAN, DEPOSIT_EUR);
    ours(FUND_INVESTMENT_IBAN, FUND_INVESTMENT_EUR);
    ours(WITHDRAWAL_IBAN, WITHDRAWAL_EUR);
  }

  @Test
  void confirmsOnceEveryBriefedPaymentIsExecutedAsSentAndAsTheLedgerExpects() {
    var redemption = randomUUID();
    var batch = randomUUID();
    var returnedPayment = randomUUID();
    store(PAYOUT, WITHDRAWAL_IBAN, "250.00", EXECUTED, redemption);
    store(REDEMPTION_TRANSFER, FUND_INVESTMENT_IBAN, "250.00", EXECUTED, batch);
    store(SUBSCRIPTION_TRANSFER, DEPOSIT_IBAN, "1000.00", EXECUTED, randomUUID());
    store(RETURN, DEPOSIT_IBAN, "50.00", EXECUTED, returnedPayment);
    ledger.pricedRedemptions.put(redemption, new BigDecimal("250.00"));
    ledger.pricedBatches.put(batch, new BigDecimal("250.00"));
    ledger.issuedSubscriptions = new BigDecimal("1000.00");
    ledger.bookedReturns.put(returnedPayment, new BigDecimal("50.00"));

    assertThat(closing())
        .contains(
            new Closing(
                PAYMENT_BATCH_SETTLED,
                TODAY.toString(),
                """
                ✅ TKF100 — all 4 payments of the approval brief executed as sent and as the ledger expects

                  DEPOSIT_EUR  2 payments  1,050.00 EUR
                  FUND_INVESTMENT_EUR  1 payment  250.00 EUR
                  WITHDRAWAL_EUR  1 payment  250.00 EUR

                  TOTAL  4 payments  1,550.00 EUR

                  Checks:
                      ✅ each debit matches the amount and account we sent
                      ✅ payouts == ledger pricing
                      ✅ transfer to withdrawal account == ledger pricing of its batch
                      ✅ transfer to fund account == subscriptions issued in the ledger
                      ✅ returns == ledger return booking"""));
  }

  @Test
  void saysNothingWhileABriefedPaymentStillAwaitsApprovalBecauseTheReminderSpeaksThen() {
    var redemption = randomUUID();
    store(PAYOUT, WITHDRAWAL_IBAN, "250.00", SUBMITTED, redemption);
    ledger.pricedRedemptions.put(redemption, new BigDecimal("250.00"));

    assertThat(closing()).isEmpty();
  }

  @Test
  void saysNothingOnADayWithNoBriefedPayments() {
    assertThat(closing()).isEmpty();
  }

  @Test
  void confirmsOnlyOnceADay() {
    executedPayoutThatTheLedgerPriced("250.00");
    settlementCheck.markPosted(closing().orElseThrow());

    assertThat(closing()).isEmpty();
  }

  @Test
  void reportsAPaymentTheBankNeverAnsweredBecauseWhetherItWentOutIsUnknown() {
    var redemption = randomUUID();
    var unanswered = store(PAYOUT, WITHDRAWAL_IBAN, "250.00", ATTEMPTED, redemption);
    ledger.pricedRedemptions.put(redemption, new BigDecimal("250.00"));

    assertThat(closing().map(Closing::message))
        .contains(
            notSettled(
                "1 payment never got an answer from the bank: " + unanswered.getEndToEndId()));
  }

  @Test
  void reportsAPaymentTheBankRejectedAtSubmission() {
    var rejected = store(RETURN, DEPOSIT_IBAN, "50.00", FAILED, randomUUID());

    assertThat(closing().map(Closing::message))
        .contains(
            notSettled(
                "1 payment rejected by the bank at submission: " + rejected.getEndToEndId()));
  }

  @Test
  void reportsADebitTheMatcherCouldNotConfirmMatchesWhatWeSent() {
    var payout = executedPayoutThatTheLedgerPriced("250.00");
    paymentCheckService.record(
        DEBIT_MISMATCH, HOLD, payout.getEndToEndId(), "the bank debited another amount");

    assertThat(closing().map(Closing::message))
        .contains(
            notSettled(
                "1 payment not confirmed to match what we sent (DEBIT_MISMATCH): "
                    + payout.getEndToEndId()));
  }

  @Test
  void reportsAPayoutTheLedgerPricedAtAnotherAmount() {
    var redemption = randomUUID();
    var payout = store(PAYOUT, WITHDRAWAL_IBAN, "250.00", EXECUTED, redemption);
    ledger.pricedRedemptions.put(redemption, new BigDecimal("240.00"));

    assertThat(closing().map(Closing::message))
        .contains(
            notSettled(
                "payouts == ledger pricing: %s sent 250.00 EUR, ledger 240.00 EUR"
                    .formatted(payout.getEndToEndId())));
  }

  @Test
  void reportsAPayoutTheLedgerNeverPriced() {
    var payout = store(PAYOUT, WITHDRAWAL_IBAN, "250.00", EXECUTED, randomUUID());

    assertThat(closing().map(Closing::message))
        .contains(
            notSettled(
                "payouts == ledger pricing: %s sent 250.00 EUR, ledger has none"
                    .formatted(payout.getEndToEndId())));
  }

  @Test
  void reportsATransferToTheWithdrawalAccountThatDiffersFromTheLedgerPricingOfItsBatch() {
    var batch = randomUUID();
    var transfer = store(REDEMPTION_TRANSFER, FUND_INVESTMENT_IBAN, "250.00", EXECUTED, batch);
    ledger.pricedBatches.put(batch, new BigDecimal("250.01"));

    assertThat(closing().map(Closing::message))
        .contains(
            notSettled(
                "transfer to withdrawal account == ledger pricing of its batch: %s sent 250.00 EUR, ledger 250.01 EUR"
                    .formatted(transfer.getEndToEndId())));
  }

  @Test
  void reportsSubscriptionsTheLedgerIssuedThatWereNeverTransferredToTheFundAccount() {
    executedPayoutThatTheLedgerPriced("250.00");
    ledger.issuedSubscriptions = new BigDecimal("100.00");

    assertThat(closing().map(Closing::message))
        .contains(
            notSettled(
                "transfer to fund account == subscriptions issued in the ledger: transferred 0.00 EUR, ledger issued 100.00 EUR"));
  }

  @Test
  void reportsAReturnTheLedgerHasNotBooked() {
    var returned = store(RETURN, DEPOSIT_IBAN, "50.00", EXECUTED, randomUUID());

    assertThat(closing().map(Closing::message))
        .contains(
            notSettled(
                "returns == ledger return booking: %s sent 50.00 EUR, ledger has none"
                    .formatted(returned.getEndToEndId())));
  }

  @Test
  void waitsWhileAPaymentWithoutAnAnswerMayStillBeInFlight() {
    ClockHolder.setClock(Clock.fixed(BEFORE_THE_BRIEF.plus(Duration.ofMinutes(10)), UTC));
    var redemption = randomUUID();
    store(PAYOUT, WITHDRAWAL_IBAN, "250.00", ATTEMPTED, redemption);
    ledger.pricedRedemptions.put(redemption, new BigDecimal("250.00"));

    assertThat(closing()).isEmpty();
  }

  @Test
  void waitsUntilTheStatementRunHasHadTimeToBookWhatItExecuted() {
    var now = BRIEF.plus(Duration.ofMinutes(10));
    ClockHolder.setClock(Clock.fixed(now, UTC));
    var returnedPayment = randomUUID();
    store(
        RETURN,
        DEPOSIT_IBAN,
        "50.00",
        EXECUTED,
        returnedPayment,
        BEFORE_THE_BRIEF,
        now.minus(Duration.ofMinutes(1)));

    assertThat(closing()).isEmpty();
  }

  @Test
  void reportsAPaymentFromAnEarlierBriefTheBankHasStillNotExecutedBecauseTheBriefCountedIt() {
    executedPayoutThatTheLedgerPriced("250.00");
    var earlier =
        store(
            PAYOUT,
            WITHDRAWAL_IBAN,
            "80.00",
            SUBMITTED,
            randomUUID(),
            PREVIOUS_BRIEF.minus(Duration.ofHours(2)),
            null);

    assertThat(closing().map(Closing::message))
        .contains(
            notSettled(
                "1 payment from earlier briefs still not executed: " + earlier.getEndToEndId()));
  }

  @Test
  void reportsANewProblemThatAppearsAfterAnotherWasReported() {
    var payout = store(PAYOUT, WITHDRAWAL_IBAN, "250.00", EXECUTED, randomUUID());
    settlementCheck.markPosted(closing().orElseThrow());
    var rejected = store(RETURN, DEPOSIT_IBAN, "50.00", FAILED, randomUUID());

    assertThat(closing().map(Closing::message))
        .contains(
            notSettled(
                "1 payment rejected by the bank at submission: " + rejected.getEndToEndId(),
                "payouts == ledger pricing: %s sent 250.00 EUR, ledger has none"
                    .formatted(payout.getEndToEndId())));
  }

  @Test
  void reportsAProblemOnlyOnceADay() {
    store(PAYOUT, WITHDRAWAL_IBAN, "250.00", EXECUTED, randomUUID());
    var reported = closing().orElseThrow();
    settlementCheck.markPosted(reported);

    assertThat(reported.outcome()).isEqualTo(PAYMENT_BATCH_NOT_SETTLED);
    assertThat(closing()).isEmpty();
  }

  @Test
  void confirmsOnceAReportedProblemIsResolved() {
    var redemption = randomUUID();
    store(PAYOUT, WITHDRAWAL_IBAN, "250.00", EXECUTED, redemption);
    settlementCheck.markPosted(closing().orElseThrow());

    ledger.pricedRedemptions.put(redemption, new BigDecimal("250.00"));

    assertThat(closing()).map(Closing::outcome).contains(PAYMENT_BATCH_SETTLED);
  }

  private Optional<Closing> closing() {
    return settlementCheck.closingFor(briefedBatches.on(TODAY), TODAY);
  }

  private static String notSettled(String... problems) {
    return "🔴 TKF100 — payments of the approval brief are not all in order\n\n"
        + Stream.of(problems).map(problem -> "  ❌ " + problem).collect(joining("\n"));
  }

  private OutgoingPayment executedPayoutThatTheLedgerPriced(String amount) {
    var redemption = randomUUID();
    ledger.pricedRedemptions.put(redemption, new BigDecimal(amount));
    return store(PAYOUT, WITHDRAWAL_IBAN, amount, EXECUTED, redemption);
  }

  private void ours(String iban, BankAccountType type) {
    given(bankAccounts.find(iban))
        .willReturn(Optional.of(new BankAccount(iban, type, TKF100, "client")));
  }

  private OutgoingPayment store(
      OutgoingPaymentType type,
      String remitterIban,
      String amount,
      OutgoingPaymentStatus status,
      UUID sourceId) {
    return store(type, remitterIban, amount, status, sourceId, BEFORE_THE_BRIEF, null);
  }

  private OutgoingPayment store(
      OutgoingPaymentType type,
      String remitterIban,
      String amount,
      OutgoingPaymentStatus status,
      UUID sourceId,
      Instant attemptedAt,
      @Nullable Instant resolvedAt) {
    return outgoingPaymentRepository.save(
        OutgoingPayment.builder()
            .endToEndId(randomUUID().toString().replace("-", ""))
            .paymentType(type)
            .sourceId(sourceId)
            .remitterIban(remitterIban)
            .beneficiaryIban(BENEFICIARY_IBAN)
            .amount(new BigDecimal(amount))
            .currency("EUR")
            .bodyHash("hash")
            .status(status)
            .attemptedAt(attemptedAt)
            .resolvedAt(resolvedAt)
            .build());
  }
}
