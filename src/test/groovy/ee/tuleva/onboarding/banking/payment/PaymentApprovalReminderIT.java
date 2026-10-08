package ee.tuleva.onboarding.banking.payment;

import static ee.tuleva.onboarding.banking.BankAccountType.DEPOSIT_EUR;
import static ee.tuleva.onboarding.banking.BankAccountType.WITHDRAWAL_EUR;
import static ee.tuleva.onboarding.banking.message.BankMessageType.INTRA_DAY_REPORT;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.ATTEMPTED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.EXECUTED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.SUBMITTED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentType.PAYOUT;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentType.RETURN;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentType.SUBSCRIPTION_TRANSFER;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest
@Import({
  PaymentApprovalReminder.class,
  BriefedBatches.class,
  PaymentAccounts.class,
  PublicHolidays.class
})
class PaymentApprovalReminderIT {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final LocalDate TODAY = LocalDate.of(2026, 9, 23);
  private static final String DEPOSIT_IBAN = "EE444444444444444444";
  private static final String WITHDRAWAL_IBAN = "EE555555555555555555";
  private static final String BENEFICIARY_IBAN = "EE666666666666666666";
  private static final Instant BEFORE_THE_BRIEF = Instant.parse("2026-09-23T13:05:00Z");
  private static final Instant AFTER_THE_BRIEF = Instant.parse("2026-09-23T13:15:00Z");
  private static final Instant YESTERDAY_BEFORE_ITS_BRIEF = Instant.parse("2026-09-22T13:05:00Z");
  private static final Instant YESTERDAY_AFTER_ITS_BRIEF = Instant.parse("2026-09-22T13:40:00Z");
  private static final Instant FETCHED_AT_16_23 = Instant.parse("2026-09-23T13:23:00Z");
  private static final Instant FETCHED_YESTERDAY_AT_17_58 = Instant.parse("2026-09-22T14:58:00Z");
  private static final Instant FETCHED_AT_16_28 = Instant.parse("2026-09-23T13:28:00Z");

  @Autowired PaymentApprovalReminder reminder;
  @Autowired BriefedBatches briefedBatches;
  @Autowired OutgoingPaymentRepository outgoingPaymentRepository;
  @Autowired JdbcClient jdbcClient;
  @MockitoBean BankAccounts bankAccounts;

  @BeforeEach
  void theAccountsAreOurs() {
    given(bankAccounts.find(DEPOSIT_IBAN))
        .willReturn(Optional.of(new BankAccount(DEPOSIT_IBAN, DEPOSIT_EUR, TKF100, "client")));
    given(bankAccounts.find(WITHDRAWAL_IBAN))
        .willReturn(
            Optional.of(new BankAccount(WITHDRAWAL_IBAN, WITHDRAWAL_EUR, TKF100, "client")));
  }

  @Test
  void
      remindsOfBriefedPaymentsTheBankHasNotExecutedPerAccountWithTheStatementTheyWereCheckedAgainst() {
    storePayment(SUBSCRIPTION_TRANSFER, DEPOSIT_IBAN, "150959.33", SUBMITTED, BEFORE_THE_BRIEF);
    storePayment(RETURN, DEPOSIT_IBAN, "5000.00", SUBMITTED, BEFORE_THE_BRIEF);
    storePayment(PAYOUT, WITHDRAWAL_IBAN, "250.00", SUBMITTED, BEFORE_THE_BRIEF);
    storeIntraDayReport(DEPOSIT_IBAN, FETCHED_AT_16_23, FETCHED_AT_16_23);

    assertThat(reminder.forPaymentsStillUnexecuted(briefedBatches.on(TODAY), TODAY))
        .contains(
            """
            🟠 TKF100 — 3 payments not yet executed by the bank

              DEPOSIT_EUR  2 payments  155,959.33 EUR  (statement 16:23)
              WITHDRAWAL_EUR  1 payment  250.00 EUR  (no processed statement)""");
  }

  @Test
  void staysSilentOnceTheStatementHasMarkedEveryBriefedPaymentExecuted() {
    storePayment(SUBSCRIPTION_TRANSFER, DEPOSIT_IBAN, "150959.33", EXECUTED, BEFORE_THE_BRIEF);
    storeIntraDayReport(DEPOSIT_IBAN, FETCHED_AT_16_23, FETCHED_AT_16_23);

    assertThat(reminder.forPaymentsStillUnexecuted(briefedBatches.on(TODAY), TODAY)).isEmpty();
  }

  @Test
  void leavesPaymentsSentAfterTheBriefToTheNextDaysBrief() {
    storePayment(RETURN, DEPOSIT_IBAN, "5000.00", SUBMITTED, AFTER_THE_BRIEF);

    assertThat(reminder.forPaymentsStillUnexecuted(briefedBatches.on(TODAY), TODAY)).isEmpty();
  }

  @Test
  void leavesAPaymentWhoseSubmissionNeverGotAnAnswerToTheIndeterminateCheck() {
    storePayment(RETURN, DEPOSIT_IBAN, "5000.00", ATTEMPTED, BEFORE_THE_BRIEF);

    assertThat(reminder.forPaymentsStillUnexecuted(briefedBatches.on(TODAY), TODAY)).isEmpty();
  }

  @Test
  void includesAPaymentSentAfterThePreviousBriefBecauseTodaysBriefListedIt() {
    storePayment(PAYOUT, WITHDRAWAL_IBAN, "250.00", SUBMITTED, YESTERDAY_AFTER_ITS_BRIEF);
    storeIntraDayReport(WITHDRAWAL_IBAN, FETCHED_YESTERDAY_AT_17_58, FETCHED_YESTERDAY_AT_17_58);

    assertThat(reminder.forPaymentsStillUnexecuted(briefedBatches.on(TODAY), TODAY))
        .contains(
            """
            🟠 TKF100 — 1 payment not yet executed by the bank

              WITHDRAWAL_EUR  1 payment  250.00 EUR  (statement 2026-09-22 17:58)""");
  }

  @Test
  void leavesAPaymentAnEarlierDaysRemindersCoveredToTheNotExecutedCheckSoItCannotRepeatForever() {
    storePayment(PAYOUT, WITHDRAWAL_IBAN, "250.00", SUBMITTED, YESTERDAY_BEFORE_ITS_BRIEF);

    assertThat(reminder.forPaymentsStillUnexecuted(briefedBatches.on(TODAY), TODAY)).isEmpty();
  }

  @Test
  void aFetchedStatementCountsOnlyOnceItHasBeenProcessed() {
    storePayment(PAYOUT, WITHDRAWAL_IBAN, "250.00", SUBMITTED, BEFORE_THE_BRIEF);
    storeIntraDayReport(WITHDRAWAL_IBAN, FETCHED_AT_16_23, FETCHED_AT_16_23);
    storeIntraDayReport(WITHDRAWAL_IBAN, FETCHED_AT_16_28, null);

    assertThat(reminder.forPaymentsStillUnexecuted(briefedBatches.on(TODAY), TODAY))
        .contains(
            """
            🟠 TKF100 — 1 payment not yet executed by the bank

              WITHDRAWAL_EUR  1 payment  250.00 EUR  (statement 16:23)""");
  }

  private void storePayment(
      OutgoingPaymentType type,
      String remitterIban,
      String amount,
      OutgoingPaymentStatus status,
      Instant attemptedAt) {
    outgoingPaymentRepository.save(
        OutgoingPayment.builder()
            .endToEndId(UUID.randomUUID().toString().replace("-", ""))
            .paymentType(type)
            .remitterIban(remitterIban)
            .beneficiaryIban(BENEFICIARY_IBAN)
            .amount(new BigDecimal(amount))
            .currency("EUR")
            .bodyHash("hash")
            .status(status)
            .attemptedAt(attemptedAt)
            .build());
  }

  private void storeIntraDayReport(String iban, Instant receivedAt, @Nullable Instant processedAt) {
    var reportDate = receivedAt.atZone(TALLINN).toLocalDate();
    jdbcClient
        .sql(
            """
            insert into banking_message (id, bank_type, request_id, tracking_id, raw_response,
              timezone, message_type, account_iban, statement_from, statement_to, processed_at,
              received_at)
            values (:id, 'SEB', 'request', 'tracking', '<Document/>', 'Europe/Tallinn',
              :messageType, :iban, :statementDate, :statementDate, :processedAt, :receivedAt)
            """)
        .param("id", UUID.randomUUID())
        .param("messageType", INTRA_DAY_REPORT.name())
        .param("iban", iban)
        .param("statementDate", processedAt == null ? null : reportDate)
        .param("processedAt", processedAt == null ? null : Timestamp.from(processedAt))
        .param("receivedAt", Timestamp.from(receivedAt))
        .update();
  }
}
