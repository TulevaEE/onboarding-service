package ee.tuleva.onboarding.banking.payment;

import static ee.tuleva.onboarding.banking.BankAccountType.DEPOSIT_EUR;
import static ee.tuleva.onboarding.banking.BankAccountType.FUND_INVESTMENT_EUR;
import static ee.tuleva.onboarding.banking.BankAccountType.WITHDRAWAL_EUR;
import static ee.tuleva.onboarding.banking.message.BankMessageType.HISTORIC_STATEMENT;
import static ee.tuleva.onboarding.banking.message.BankMessageType.INTRA_DAY_REPORT;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.SUBMITTED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentType.PAYOUT;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentType.REDEMPTION_TRANSFER;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentType.RETURN;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentType.SUBSCRIPTION_TRANSFER;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.message.BankMessageType;
import ee.tuleva.onboarding.banking.message.BookedBalanceReader;
import ee.tuleva.onboarding.banking.payment.PaymentApprovalBrief.ProjectedBalance;
import ee.tuleva.onboarding.banking.statement.BankStatementExtractor;
import ee.tuleva.onboarding.banking.xml.Iso20022Marshaller;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest
@Import({
  PaymentApprovalBriefService.class,
  BatchTies.class,
  BookedBalanceReader.class,
  BankStatementExtractor.class,
  Iso20022Marshaller.class,
  PaymentApprovalBriefServiceIT.NothingHeld.class
})
class PaymentApprovalBriefServiceIT {

  @TestConfiguration
  static class NothingHeld {
    @Bean
    HeldPayouts heldPayouts() {
      return batchId -> ZERO;
    }
  }

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final LocalDate TODAY = LocalDate.of(2026, 9, 23);
  private static final String DEPOSIT_IBAN = "EE651010220306497226";
  private static final String BENEFICIARY_IBAN = "EE241010220306719221";
  private static final String FUND_INVESTMENT_IBAN = "EE444444444444444444";
  private static final String WITHDRAWAL_IBAN = "EE555555555555555555";
  private static final Instant FOUR_PM_REPORT = Instant.parse("2026-09-23T13:00:05Z");
  private static final Instant HALF_PAST_FOUR_REPORT = Instant.parse("2026-09-23T13:30:05Z");
  private static final Instant YESTERDAYS_STATEMENT = Instant.parse("2026-09-23T01:00:33Z");

  @Autowired PaymentApprovalBriefService service;
  @Autowired OutgoingPaymentRepository outgoingPaymentRepository;
  @Autowired JdbcClient jdbcClient;
  @MockitoBean BankAccounts bankAccounts;

  @BeforeEach
  void depositAccountIsOurs() {
    given(bankAccounts.find(DEPOSIT_IBAN))
        .willReturn(Optional.of(new BankAccount(DEPOSIT_IBAN, DEPOSIT_EUR, TKF100, "client")));
  }

  @Test
  void paymentsTheMatcherHasNotYetSeenAreSubtractedFromTheBalanceOfTheLastProcessedStatement() {
    storeIntraDayReport(DEPOSIT_IBAN, FOUR_PM_REPORT, "175345.82", FOUR_PM_REPORT);
    storeSubmittedPayments();

    var account = service.build(TODAY, List.of()).accounts().getFirst();

    assertThat(account.projectedBalance())
        .isEqualTo(new ProjectedBalance(new BigDecimal("19386.49"), ZERO, FOUR_PM_REPORT));
    assertThat(account.goesNegative()).isFalse();
  }

  @Test
  void aNewerReportThatAlreadyCarriesTheDebitsIsIgnoredUntilTheMatcherHasProcessedIt() {
    storeIntraDayReport(DEPOSIT_IBAN, FOUR_PM_REPORT, "175345.82", FOUR_PM_REPORT);
    storeIntraDayReport(DEPOSIT_IBAN, HALF_PAST_FOUR_REPORT, "24386.49", null);
    storeSubmittedPayments();

    var account = service.build(TODAY, List.of()).accounts().getFirst();

    assertThat(account.projectedBalance())
        .isEqualTo(new ProjectedBalance(new BigDecimal("19386.49"), ZERO, FOUR_PM_REPORT));
    assertThat(account.goesNegative()).isFalse();
  }

  @Test
  void beforeTodaysFirstReportYesterdaysClosingStatementIsTheBalance() {
    storeHistoricStatement(DEPOSIT_IBAN, YESTERDAYS_STATEMENT, "175345.82", YESTERDAYS_STATEMENT);
    storeSubmittedPayments();

    var account = service.build(TODAY, List.of()).accounts().getFirst();

    assertThat(account.projectedBalance())
        .isEqualTo(new ProjectedBalance(new BigDecimal("19386.49"), ZERO, YESTERDAYS_STATEMENT));
  }

  @Test
  void
      theTransferStillInTheFundAccountAtFourIsAddedToTheWithdrawalAccountBeforeItsPayoutsAreSubtracted() {
    given(bankAccounts.find(FUND_INVESTMENT_IBAN))
        .willReturn(
            Optional.of(
                new BankAccount(FUND_INVESTMENT_IBAN, FUND_INVESTMENT_EUR, TKF100, "client")));
    given(bankAccounts.find(WITHDRAWAL_IBAN))
        .willReturn(
            Optional.of(new BankAccount(WITHDRAWAL_IBAN, WITHDRAWAL_EUR, TKF100, "client")));
    storeIntraDayReport(WITHDRAWAL_IBAN, FOUR_PM_REPORT, "0.00", FOUR_PM_REPORT);
    storePayment(REDEMPTION_TRANSFER, FUND_INVESTMENT_IBAN, WITHDRAWAL_IBAN, "12345.67");
    storePayment(PAYOUT, WITHDRAWAL_IBAN, BENEFICIARY_IBAN, "12000.00");
    storePayment(PAYOUT, WITHDRAWAL_IBAN, BENEFICIARY_IBAN, "345.67");

    var withdrawal =
        service.build(TODAY, List.of()).accounts().stream()
            .filter(account -> account.accountName().equals("WITHDRAWAL_EUR"))
            .findFirst()
            .orElseThrow();

    assertThat(withdrawal.projectedBalance())
        .isEqualTo(
            new ProjectedBalance(
                new BigDecimal("0.00"), new BigDecimal("12345.67"), FOUR_PM_REPORT));
    assertThat(withdrawal.goesNegative()).isFalse();
    assertThat(withdrawal.coveredOnlyByIncomingTransfer()).isTrue();
  }

  @Test
  void anAccountWithNoProcessedStatementShowsNoProjection() {
    storeIntraDayReport(DEPOSIT_IBAN, FOUR_PM_REPORT, "175345.82", null);
    storeSubmittedPayments();

    var account = service.build(TODAY, List.of()).accounts().getFirst();

    assertThat(account.projectedBalance()).isNull();
    assertThat(account.goesNegative()).isFalse();
  }

  private void storeSubmittedPayments() {
    storePayment(SUBSCRIPTION_TRANSFER, "150959.33");
    storePayment(RETURN, "5000.00");
  }

  private void storePayment(OutgoingPaymentType type, String amount) {
    storePayment(type, DEPOSIT_IBAN, BENEFICIARY_IBAN, amount);
  }

  private void storePayment(
      OutgoingPaymentType type, String remitterIban, String beneficiaryIban, String amount) {
    outgoingPaymentRepository.save(
        OutgoingPayment.builder()
            .endToEndId(UUID.randomUUID().toString())
            .paymentType(type)
            .remitterIban(remitterIban)
            .beneficiaryIban(beneficiaryIban)
            .amount(new BigDecimal(amount))
            .currency("EUR")
            .bodyHash("hash")
            .status(SUBMITTED)
            .attemptedAt(Instant.parse("2026-09-23T12:10:00Z"))
            .build());
  }

  private void storeIntraDayReport(
      String iban, Instant receivedAt, String interimBooked, @Nullable Instant processedAt) {
    var reportedUntil = receivedAt.atZone(TALLINN);
    store(
        iban,
        INTRA_DAY_REPORT,
        receivedAt,
        processedAt,
        reportedUntil.toLocalDate(),
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.052.001.02">
          <BkToCstmrAcctRpt>
            <GrpHdr><MsgId>report</MsgId><CreDtTm>%1$s</CreDtTm></GrpHdr>
            <Rpt>
              <Id>report</Id>
              <CreDtTm>%1$s</CreDtTm>
              <FrToDt><FrDtTm>%2$sT00:00:00+03:00</FrDtTm><ToDtTm>%1$s</ToDtTm></FrToDt>
              %3$s
              %4$s
              %5$s
              %6$s
            </Rpt>
          </BkToCstmrAcctRpt>
        </Document>
        """
            .formatted(
                reportedUntil.toOffsetDateTime(),
                reportedUntil.toLocalDate(),
                account(iban),
                balance("OPBD", reportedUntil.toLocalDate(), interimBooked),
                balance("ITBD", reportedUntil.toLocalDate(), interimBooked),
                balance("ITAV", reportedUntil.toLocalDate(), "180345.82")));
  }

  private void storeHistoricStatement(
      String iban, Instant receivedAt, String closingBooked, @Nullable Instant processedAt) {
    var statementDate = receivedAt.atZone(TALLINN).toLocalDate().minusDays(1);
    store(
        iban,
        HISTORIC_STATEMENT,
        receivedAt,
        processedAt,
        statementDate,
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.053.001.02">
          <BkToCstmrStmt>
            <GrpHdr><MsgId>statement</MsgId><CreDtTm>%1$sT04:00:33+03:00</CreDtTm></GrpHdr>
            <Stmt>
              <Id>statement</Id>
              <CreDtTm>%1$sT04:00:33+03:00</CreDtTm>
              <FrToDt><FrDtTm>%2$sT00:00:00+03:00</FrDtTm><ToDtTm>%2$sT23:59:59+03:00</ToDtTm></FrToDt>
              %3$s
              %4$s
              %5$s
              %6$s
            </Stmt>
          </BkToCstmrStmt>
        </Document>
        """
            .formatted(
                statementDate.plusDays(1),
                statementDate,
                account(iban),
                balance("OPBD", statementDate, closingBooked),
                balance("CLBD", statementDate, closingBooked),
                balance("CLAV", statementDate, "180345.82")));
  }

  private static String account(String iban) {
    return """
        <Acct>
          <Id><IBAN>%s</IBAN></Id>
          <Ownr><Nm>BGW TESTCLIENT1</Nm><Id><OrgId><Othr><Id>22255887</Id></Othr></OrgId></Id></Ownr>
        </Acct>
        """
        .formatted(iban);
  }

  private static String balance(String type, LocalDate date, String amount) {
    return """
        <Bal>
          <Tp><CdOrPrtry><Cd>%s</Cd></CdOrPrtry></Tp>
          <Amt Ccy="EUR">%s</Amt>
          <CdtDbtInd>CRDT</CdtDbtInd>
          <Dt><Dt>%s</Dt></Dt>
        </Bal>
        """
        .formatted(type, amount, date);
  }

  private void store(
      String iban,
      BankMessageType type,
      Instant receivedAt,
      @Nullable Instant processedAt,
      LocalDate statementDate,
      String rawResponse) {
    jdbcClient
        .sql(
            """
            insert into banking_message (id, bank_type, request_id, tracking_id, raw_response,
              timezone, message_type, account_iban, statement_from, statement_to, processed_at,
              received_at)
            values (:id, 'SEB', 'request', 'tracking', :rawResponse, 'Europe/Tallinn',
              :messageType, :iban, :statementDate, :statementDate, :processedAt, :receivedAt)
            """)
        .param("id", UUID.randomUUID())
        .param("rawResponse", rawResponse)
        .param("messageType", type.name())
        .param("iban", iban)
        .param("statementDate", processedAt == null ? null : statementDate)
        .param("processedAt", processedAt == null ? null : Timestamp.from(processedAt))
        .param("receivedAt", Timestamp.from(receivedAt))
        .update();
  }
}
