package ee.tuleva.onboarding.banking.payment;

import static ee.tuleva.onboarding.banking.BankAccountType.DEPOSIT_EUR;
import static ee.tuleva.onboarding.banking.message.BankMessageType.HISTORIC_STATEMENT;
import static ee.tuleva.onboarding.banking.message.BankMessageType.INTRA_DAY_REPORT;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.SUBMITTED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentType.RETURN;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentType.SUBSCRIPTION_TRANSFER;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
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
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest
@Import({
  PaymentApprovalBriefService.class,
  BatchTies.class,
  BookedBalanceReader.class,
  BankStatementExtractor.class,
  Iso20022Marshaller.class
})
class PaymentApprovalBriefServiceIT {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final LocalDate TODAY = LocalDate.of(2026, 9, 23);
  private static final String DEPOSIT_IBAN = "EE651010220306497226";
  private static final String BENEFICIARY_IBAN = "EE241010220306719221";
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
    storeIntraDayReport(FOUR_PM_REPORT, "175345.82", FOUR_PM_REPORT);
    storeSubmittedPayments();

    var account = service.build(TODAY, List.of()).accounts().getFirst();

    assertThat(account.projectedBalance())
        .isEqualTo(new ProjectedBalance(new BigDecimal("19386.49"), FOUR_PM_REPORT));
    assertThat(account.goesNegative()).isFalse();
  }

  @Test
  void aNewerReportThatAlreadyCarriesTheDebitsIsIgnoredUntilTheMatcherHasProcessedIt() {
    storeIntraDayReport(FOUR_PM_REPORT, "175345.82", FOUR_PM_REPORT);
    storeIntraDayReport(HALF_PAST_FOUR_REPORT, "24386.49", null);
    storeSubmittedPayments();

    var account = service.build(TODAY, List.of()).accounts().getFirst();

    assertThat(account.projectedBalance())
        .isEqualTo(new ProjectedBalance(new BigDecimal("19386.49"), FOUR_PM_REPORT));
    assertThat(account.goesNegative()).isFalse();
  }

  @Test
  void beforeTodaysFirstReportYesterdaysClosingStatementIsTheBalance() {
    storeHistoricStatement(YESTERDAYS_STATEMENT, "175345.82", YESTERDAYS_STATEMENT);
    storeSubmittedPayments();

    var account = service.build(TODAY, List.of()).accounts().getFirst();

    assertThat(account.projectedBalance())
        .isEqualTo(new ProjectedBalance(new BigDecimal("19386.49"), YESTERDAYS_STATEMENT));
  }

  @Test
  void anAccountWithNoProcessedStatementShowsNoProjection() {
    storeIntraDayReport(FOUR_PM_REPORT, "175345.82", null);
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
    outgoingPaymentRepository.save(
        OutgoingPayment.builder()
            .endToEndId(UUID.randomUUID().toString())
            .paymentType(type)
            .remitterIban(DEPOSIT_IBAN)
            .beneficiaryIban(BENEFICIARY_IBAN)
            .amount(new BigDecimal(amount))
            .currency("EUR")
            .bodyHash("hash")
            .status(SUBMITTED)
            .attemptedAt(Instant.parse("2026-09-23T12:10:00Z"))
            .build());
  }

  private void storeIntraDayReport(
      Instant receivedAt, String interimBooked, @Nullable Instant processedAt) {
    var reportedUntil = receivedAt.atZone(TALLINN);
    store(
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
                account(),
                balance("OPBD", reportedUntil.toLocalDate(), interimBooked),
                balance("ITBD", reportedUntil.toLocalDate(), interimBooked),
                balance("ITAV", reportedUntil.toLocalDate(), "180345.82")));
  }

  private void storeHistoricStatement(
      Instant receivedAt, String closingBooked, @Nullable Instant processedAt) {
    var statementDate = receivedAt.atZone(TALLINN).toLocalDate().minusDays(1);
    store(
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
                account(),
                balance("OPBD", statementDate, closingBooked),
                balance("CLBD", statementDate, closingBooked),
                balance("CLAV", statementDate, "180345.82")));
  }

  private static String account() {
    return """
        <Acct>
          <Id><IBAN>%s</IBAN></Id>
          <Ownr><Nm>BGW TESTCLIENT1</Nm><Id><OrgId><Othr><Id>22255887</Id></Othr></OrgId></Id></Ownr>
        </Acct>
        """
        .formatted(DEPOSIT_IBAN);
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
        .param("iban", DEPOSIT_IBAN)
        .param("statementDate", processedAt == null ? null : statementDate)
        .param("processedAt", processedAt == null ? null : Timestamp.from(processedAt))
        .param("receivedAt", Timestamp.from(receivedAt))
        .update();
  }
}
