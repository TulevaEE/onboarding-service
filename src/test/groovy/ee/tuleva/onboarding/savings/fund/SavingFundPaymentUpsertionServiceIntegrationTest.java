package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_INVESTMENT_CASH_CLEARING;
import static ee.tuleva.onboarding.ledger.SystemAccount.INCOMING_PAYMENTS_CLEARING;
import static ee.tuleva.onboarding.swedbank.statement.BankAccountType.FUND_INVESTMENT_EUR;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.config.TestSchedulerLockConfiguration;
import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.swedbank.fetcher.SwedbankAccountConfiguration;
import ee.tuleva.onboarding.swedbank.fetcher.SwedbankMessage;
import ee.tuleva.onboarding.swedbank.fetcher.SwedbankMessageRepository;
import ee.tuleva.onboarding.swedbank.processor.SwedbankMessageDelegator;
import ee.tuleva.onboarding.time.ClockHolder;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestSchedulerLockConfiguration.class)
@Transactional
class SavingFundPaymentUpsertionServiceIntegrationTest {

  @Autowired private SavingFundPaymentRepository repository;
  @Autowired private SwedbankMessageRepository swedbankMessageRepository;
  @Autowired private SwedbankMessageDelegator delegator;
  @Autowired private LedgerService ledgerService;
  @Autowired private SwedbankAccountConfiguration swedbankAccountConfiguration;

  private static final Instant NOW = Instant.parse("2025-10-01T12:00:00Z");

  @BeforeEach
  void setUp() {
    ClockHolder.setClock(Clock.fixed(NOW, ZoneId.of("UTC")));
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  // XML template with a single CREDIT transaction
  private static final String XML_TEMPLATE =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?> "
          + "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:camt.052.001.02\"> "
          + "<BkToCstmrAcctRpt> "
          + "<GrpHdr> <MsgId>test</MsgId> <CreDtTm>2025-10-01T12:00:00</CreDtTm> </GrpHdr> "
          + "<Rpt> "
          + "<Id>test-1</Id> "
          + "<CreDtTm>2025-10-01T12:00:00</CreDtTm> "
          + "<FrToDt> "
          + "<FrDtTm>2025-10-01T00:00:00</FrDtTm> "
          + "<ToDtTm>2025-10-01T12:00:00</ToDtTm> "
          + "</FrToDt> "
          + "<Acct> "
          + "<Id> <IBAN>EE442200221092874625</IBAN> </Id> "
          + "<Ownr> <Nm>TULEVA FONDID AS</Nm> "
          + "<Id> <OrgId> <Othr> <Id>14118923</Id> </Othr> </OrgId> </Id> "
          + "</Ownr> "
          + "</Acct> "
          + "<Ntry> "
          + "<NtryRef>2025100112345-1</NtryRef>"
          + "<Amt Ccy=\"EUR\">100.50</Amt> "
          + "<CdtDbtInd>CRDT</CdtDbtInd> "
          + "<Sts>BOOK</Sts> "
          + "<BookgDt> <Dt>2025-10-01</Dt> </BookgDt> "
          + "<NtryDtls> <TxDtls> "
          + "<Refs> <AcctSvcrRef>2025100112345-1</AcctSvcrRef> </Refs> "
          + "<AmtDtls> <TxAmt> <Amt Ccy=\"EUR\">100.50</Amt> </TxAmt> </AmtDtls> "
          + "<RltdPties> "
          + "<Dbtr> <Nm>Jüri Tamm</Nm> "
          + "<Id> <PrvtId> <Othr> <Id>39910273027</Id> </Othr> </PrvtId> </Id> "
          + "</Dbtr> "
          + "<DbtrAcct> <Id> <IBAN>EE157700771001802057</IBAN> </Id> </DbtrAcct> "
          + "</RltdPties> "
          + "<RmtInf> <Ustrd>Test payment</Ustrd> </RmtInf> "
          + "</TxDtls> </NtryDtls> "
          + "</Ntry> "
          + "</Rpt> "
          + "</BkToCstmrAcctRpt> "
          + "</Document>";

  @Test
  void endToEnd_processesXmlAndStoresPaymentsInDatabase() {
    // when
    processXmlMessage(XML_TEMPLATE);

    // then
    assertThat(repository.findAll()).hasSize(1);
    var savedPayment = repository.findAll().iterator().next();
    assertThat(savedPayment.getAmount()).isEqualByComparingTo(new BigDecimal("100.50"));
    assertThat(savedPayment.getCurrency()).isEqualTo(Currency.EUR);
    assertThat(savedPayment.getDescription()).isEqualTo("Test payment");
    assertThat(savedPayment.getRemitterIban()).isEqualTo("EE157700771001802057");
    assertThat(savedPayment.getRemitterIdCode()).isEqualTo("39910273027");
    assertThat(savedPayment.getRemitterName()).isEqualTo("Jüri Tamm");
    assertThat(savedPayment.getBeneficiaryIban()).isEqualTo("EE442200221092874625");
    assertThat(savedPayment.getBeneficiaryIdCode()).isEqualTo("14118923");
    assertThat(savedPayment.getBeneficiaryName()).isEqualTo("TULEVA FONDID AS");
    assertThat(savedPayment.getExternalId()).isEqualTo("2025100112345-1");
    assertThat(savedPayment.getStatus()).isEqualTo(SavingFundPayment.Status.RECEIVED);
    assertThat(savedPayment.getReceivedBefore()).isEqualTo(Instant.parse("2025-10-01T09:00:00Z"));
  }

  @Test
  void doesNotProcessFailedMessages() {
    // given - a failed message with malformed XML
    var failedMessage =
        SwedbankMessage.builder()
            .requestId("test-failed")
            .trackingId("test-failed")
            .rawResponse("<malformed xml")
            .receivedAt(NOW.minus(Duration.ofHours(2)))
            .failedAt(NOW.minus(Duration.ofHours(1)))
            .build();
    var savedFailedMessage = swedbankMessageRepository.save(failedMessage);

    // and a successful message with valid XML
    var successMessage =
        SwedbankMessage.builder()
            .requestId("test-success")
            .trackingId("test-success")
            .rawResponse(XML_TEMPLATE)
            .receivedAt(NOW)
            .build();
    var savedSuccessMessage = swedbankMessageRepository.save(successMessage);

    // when
    delegator.processMessages();

    // then - failed message should not be processed
    var failedMessageAfter =
        swedbankMessageRepository.findById(savedFailedMessage.getId()).orElseThrow();
    assertThat(failedMessageAfter.getProcessedAt()).isNull();
    assertThat(failedMessageAfter.getFailedAt()).isEqualTo(NOW.minus(Duration.ofHours(1)));

    // and successful message should be processed
    var successMessageAfter =
        swedbankMessageRepository.findById(savedSuccessMessage.getId()).orElseThrow();
    assertThat(successMessageAfter.getProcessedAt()).isNotNull();
    assertThat(successMessageAfter.getFailedAt()).isNull();

    // and only one payment should be created from the successful message
    assertThat(repository.findAll()).hasSize(1);
    var savedPayment = repository.findAll().iterator().next();
    assertThat(savedPayment.getExternalId()).isEqualTo("2025100112345-1");
  }

  @Test
  void outgoingPaymentsAreMovedToProcessedRightAway() {
    // given - XML with DEBIT transaction (outgoing payment, negative amount)
    var debitXml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?> "
            + "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:camt.052.001.02\"> "
            + "<BkToCstmrAcctRpt> "
            + "<GrpHdr> <MsgId>test</MsgId> <CreDtTm>2025-10-01T12:00:00</CreDtTm> </GrpHdr> "
            + "<Rpt> "
            + "<Id>test-2</Id> "
            + "<CreDtTm>2025-10-01T12:00:00</CreDtTm> "
            + "<FrToDt> "
            + "<FrDtTm>2025-10-01T00:00:00</FrDtTm> "
            + "<ToDtTm>2025-10-01T12:00:00</ToDtTm> "
            + "</FrToDt> "
            + "<Acct> "
            + "<Id> <IBAN>EE442200221092874625</IBAN> </Id> "
            + "<Ownr> <Nm>TULEVA FONDID AS</Nm> "
            + "<Id> <OrgId> <Othr> <Id>14118923</Id> </Othr> </OrgId> </Id> "
            + "</Ownr> "
            + "</Acct> "
            + "<Ntry> "
            + "<NtryRef>2025100112346-1</NtryRef>"
            + "<Amt Ccy=\"EUR\">50.00</Amt> "
            + "<CdtDbtInd>DBIT</CdtDbtInd> "
            + "<Sts>BOOK</Sts> "
            + "<BookgDt> <Dt>2025-10-01</Dt> </BookgDt> "
            + "<NtryDtls> <TxDtls> "
            + "<Refs> <AcctSvcrRef>2025100112346-1</AcctSvcrRef> </Refs> "
            + "<AmtDtls> <TxAmt> <Amt Ccy=\"EUR\">50.00</Amt> </TxAmt> </AmtDtls> "
            + "<RltdPties> "
            + "<Cdtr> <Nm>External Party</Nm> </Cdtr> "
            + "<CdtrAcct> <Id> <IBAN>EE999999999999999999</IBAN> </Id> </CdtrAcct> "
            + "</RltdPties> "
            + "<RmtInf> <Ustrd>Outgoing payment</Ustrd> </RmtInf> "
            + "</TxDtls> </NtryDtls> "
            + "</Ntry> "
            + "</Rpt> "
            + "</BkToCstmrAcctRpt> "
            + "</Document>";

    // when
    processXmlMessage(debitXml);

    // then
    assertThat(repository.findAll()).hasSize(1);
    var savedPayment = repository.findAll().iterator().next();
    assertThat(savedPayment.getAmount()).isEqualByComparingTo(new BigDecimal("-50.00"));
    assertThat(savedPayment.getStatus()).isEqualTo(SavingFundPayment.Status.PROCESSED);
    assertThat(savedPayment.getDescription()).isEqualTo("Outgoing payment");
  }

  @Test
  void paymentsFromNonDepositAccountsAreNotProcessed() {
    // given - XML with WITHDRAWAL_EUR account IBAN
    var withdrawalAccountXml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?> "
            + "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:camt.052.001.02\"> "
            + "<BkToCstmrAcctRpt> "
            + "<GrpHdr> <MsgId>test</MsgId> <CreDtTm>2025-10-01T12:00:00</CreDtTm> </GrpHdr> "
            + "<Rpt> "
            + "<Id>test-withdrawal</Id> "
            + "<CreDtTm>2025-10-01T12:00:00</CreDtTm> "
            + "<FrToDt> "
            + "<FrDtTm>2025-10-01T00:00:00</FrDtTm> "
            + "<ToDtTm>2025-10-01T12:00:00</ToDtTm> "
            + "</FrToDt> "
            + "<Acct> "
            + "<Id> <IBAN>EE987700771001802057</IBAN> </Id> "
            + "<Ownr> <Nm>TULEVA FONDID AS</Nm> "
            + "<Id> <OrgId> <Othr> <Id>14118923</Id> </Othr> </OrgId> </Id> "
            + "</Ownr> "
            + "</Acct> "
            + "<Ntry> "
            + "<NtryRef>2025100112348-1</NtryRef>"
            + "<Amt Ccy=\"EUR\">200.00</Amt> "
            + "<CdtDbtInd>CRDT</CdtDbtInd> "
            + "<Sts>BOOK</Sts> "
            + "<BookgDt> <Dt>2025-10-01</Dt> </BookgDt> "
            + "<NtryDtls> <TxDtls> "
            + "<Refs> <AcctSvcrRef>2025100112348-1</AcctSvcrRef> </Refs> "
            + "<AmtDtls> <TxAmt> <Amt Ccy=\"EUR\">200.00</Amt> </TxAmt> </AmtDtls> "
            + "<RltdPties> "
            + "<Dbtr> <Nm>Mari Mets</Nm> "
            + "<Id> <PrvtId> <Othr> <Id>48001010123</Id> </Othr> </PrvtId> </Id> "
            + "</Dbtr> "
            + "<DbtrAcct> <Id> <IBAN>EE111222333444555666</IBAN> </Id> </DbtrAcct> "
            + "</RltdPties> "
            + "<RmtInf> <Ustrd>Payment to withdrawal account</Ustrd> </RmtInf> "
            + "</TxDtls> </NtryDtls> "
            + "</Ntry> "
            + "</Rpt> "
            + "</BkToCstmrAcctRpt> "
            + "</Document>";

    // when
    processXmlMessage(withdrawalAccountXml);

    // then - no payment should be created from withdrawal account
    assertThat(repository.findAll()).isEmpty();
  }

  @Test
  void zeroAmountPaymentsAreMovedToProcessedRightAway() {
    // given - XML with zero amount
    var zeroAmountXml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?> "
            + "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:camt.052.001.02\"> "
            + "<BkToCstmrAcctRpt> "
            + "<GrpHdr> <MsgId>test</MsgId> <CreDtTm>2025-10-01T12:00:00</CreDtTm> </GrpHdr> "
            + "<Rpt> "
            + "<Id>test-3</Id> "
            + "<CreDtTm>2025-10-01T12:00:00</CreDtTm> "
            + "<FrToDt> "
            + "<FrDtTm>2025-10-01T00:00:00</FrDtTm> "
            + "<ToDtTm>2025-10-01T12:00:00</ToDtTm> "
            + "</FrToDt> "
            + "<Acct> "
            + "<Id> <IBAN>EE442200221092874625</IBAN> </Id> "
            + "<Ownr> <Nm>TULEVA FONDID AS</Nm> "
            + "<Id> <OrgId> <Othr> <Id>14118923</Id> </Othr> </OrgId> </Id> "
            + "</Ownr> "
            + "</Acct> "
            + "<Ntry> "
            + "<NtryRef>2025100112347-1</NtryRef>"
            + "<Amt Ccy=\"EUR\">0.00</Amt> "
            + "<CdtDbtInd>CRDT</CdtDbtInd> "
            + "<Sts>BOOK</Sts> "
            + "<BookgDt> <Dt>2025-10-01</Dt> </BookgDt> "
            + "<NtryDtls> <TxDtls> "
            + "<Refs> <AcctSvcrRef>2025100112347-1</AcctSvcrRef> </Refs> "
            + "<AmtDtls> <TxAmt> <Amt Ccy=\"EUR\">0.00</Amt> </TxAmt> </AmtDtls> "
            + "<RltdPties> "
            + "<Dbtr> <Nm>Some Person</Nm> </Dbtr> "
            + "<DbtrAcct> <Id> <IBAN>EE888888888888888888</IBAN> </Id> </DbtrAcct> "
            + "</RltdPties> "
            + "<RmtInf> <Ustrd>Zero payment</Ustrd> </RmtInf> "
            + "</TxDtls> </NtryDtls> "
            + "</Ntry> "
            + "</Rpt> "
            + "</BkToCstmrAcctRpt> "
            + "</Document>";

    // when
    processXmlMessage(zeroAmountXml);

    // then
    assertThat(repository.findAll()).hasSize(1);
    var savedPayment = repository.findAll().iterator().next();
    assertThat(savedPayment.getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(savedPayment.getStatus()).isEqualTo(SavingFundPayment.Status.PROCESSED);
    assertThat(savedPayment.getDescription()).isEqualTo("Zero payment");
  }

  @Test
  void outgoingPaymentToInvestmentAccountCreatesLedgerEntry() {
    // given - get the actual INVESTMENT_EUR IBAN from configuration
    var investmentIban = swedbankAccountConfiguration.getAccountIban(FUND_INVESTMENT_EUR);

    // and - XML with outgoing DEBIT transaction to INVESTMENT_EUR account
    var outgoingToInvestmentXml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?> "
            + "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:camt.052.001.02\"> "
            + "<BkToCstmrAcctRpt> "
            + "<GrpHdr> <MsgId>test</MsgId> <CreDtTm>2025-10-01T12:00:00</CreDtTm> </GrpHdr> "
            + "<Rpt> "
            + "<Id>test-investment</Id> "
            + "<CreDtTm>2025-10-01T12:00:00</CreDtTm> "
            + "<FrToDt> "
            + "<FrDtTm>2025-10-01T00:00:00</FrDtTm> "
            + "<ToDtTm>2025-10-01T12:00:00</ToDtTm> "
            + "</FrToDt> "
            + "<Acct> "
            + "<Id> <IBAN>EE442200221092874625</IBAN> </Id> "
            + "<Ownr> <Nm>TULEVA FONDID AS</Nm> "
            + "<Id> <OrgId> <Othr> <Id>14118923</Id> </Othr> </OrgId> </Id> "
            + "</Ownr> "
            + "</Acct> "
            + "<Ntry> "
            + "<NtryRef>2025100112350-1</NtryRef>"
            + "<Amt Ccy=\"EUR\">100.50</Amt> "
            + "<CdtDbtInd>DBIT</CdtDbtInd> "
            + "<Sts>BOOK</Sts> "
            + "<BookgDt> <Dt>2025-10-01</Dt> </BookgDt> "
            + "<NtryDtls> <TxDtls> "
            + "<Refs> <AcctSvcrRef>2025100112350-1</AcctSvcrRef> </Refs> "
            + "<AmtDtls> <TxAmt> <Amt Ccy=\"EUR\">100.50</Amt> </TxAmt> </AmtDtls> "
            + "<RltdPties> "
            + "<Cdtr> <Nm>Tuleva Fondid AS</Nm> </Cdtr> "
            + "<CdtrAcct> <Id> <IBAN>"
            + investmentIban
            + "</IBAN> </Id> </CdtrAcct> "
            + "</RltdPties> "
            + "<RmtInf> <Ustrd>Transfer to investment account</Ustrd> </RmtInf> "
            + "</TxDtls> </NtryDtls> "
            + "</Ntry> "
            + "</Rpt> "
            + "</BkToCstmrAcctRpt> "
            + "</Document>";

    // when
    processXmlMessage(outgoingToInvestmentXml);

    // then - payment is created and marked as PROCESSED
    assertThat(repository.findAll()).hasSize(1);
    var savedPayment = repository.findAll().getFirst();
    assertThat(savedPayment.getAmount()).isEqualByComparingTo(new BigDecimal("-100.50"));
    assertThat(savedPayment.getStatus()).isEqualTo(SavingFundPayment.Status.PROCESSED);
    assertThat(savedPayment.getBeneficiaryIban()).isEqualTo(investmentIban);

    // and - ledger entry is created: INCOMING_PAYMENTS_CLEARING decreases,
    // FUND_INVESTMENT_CASH_CLEARING increases
    var incomingPaymentsAccount = ledgerService.getSystemAccount(INCOMING_PAYMENTS_CLEARING);
    var fundInvestmentAccount = ledgerService.getSystemAccount(FUND_INVESTMENT_CASH_CLEARING);

    assertThat(incomingPaymentsAccount.getBalance())
        .isEqualByComparingTo(new BigDecimal("-100.50"));
    assertThat(fundInvestmentAccount.getBalance()).isEqualByComparingTo(new BigDecimal("100.50"));
  }

  @Nested
  @Transactional
  class PaymentMatchingTests {

    @Autowired private SavingFundPaymentRepository repository;
    @Autowired private SwedbankMessageRepository swedbankMessageRepository;
    @Autowired private SwedbankMessageDelegator delegator;

    @Test
    void findsExistingPaymentByExternalId_updatesExistingPayment() {
      // given - existing payment from Montonio (no externalId yet)
      var existingPayment =
          paymentMatchingXmlTemplate()
              .externalId(null) // Montonio payment doesn't have externalId
              .build();
      var existingId = repository.savePaymentData(existingPayment);

      // when - XML with same IBAN+description but with externalId
      processXmlMessage(XML_TEMPLATE);

      // then - existing payment is updated with externalId, not a new one created
      assertThat(repository.findAll()).hasSize(1);
      var updated = repository.findAll().iterator().next();
      assertThat(updated.getId()).isEqualTo(existingId);
      assertThat(updated.getExternalId()).isEqualTo("2025100112345-1");
      assertThat(updated.getDescription()).isEqualTo("Test payment");
      assertThat(updated.getStatus()).isEqualTo(SavingFundPayment.Status.RECEIVED);
      // Montonio payment didn't have receivedBefore, but Swedbank report adds it
      assertThat(updated.getReceivedBefore()).isEqualTo(Instant.parse("2025-10-01T09:00:00Z"));
    }

    @Test
    void findsExistingPaymentByExternalId_doesNotCreateDuplicate() {
      // given - payment already exists with externalId from previous bank statement
      var existingReceivedBefore = Instant.parse("2025-09-30T10:00:00Z");
      var existingPayment =
          paymentMatchingXmlTemplate()
              .description("Initial payment")
              .receivedBefore(existingReceivedBefore)
              .build();
      var existingId = repository.savePaymentData(existingPayment);
      repository.changeStatus(existingId, SavingFundPayment.Status.RECEIVED);

      // when - same XML arrives again (duplicate bank statement)
      processXmlMessage(XML_TEMPLATE);

      // then - no new payment is created, no update is performed (aborts early)
      assertThat(repository.findAll()).hasSize(1);
      var payment = repository.findByExternalId("2025100112345-1");
      assertThat(payment).isPresent();
      assertThat(payment.get().getId()).isEqualTo(existingId);
      assertThat(payment.get().getDescription()).isEqualTo("Initial payment");
      assertThat(payment.get().getStatus()).isEqualTo(SavingFundPayment.Status.RECEIVED);
      assertThat(payment.get().getReceivedBefore()).isEqualTo(existingReceivedBefore);
    }

    @Test
    void doesNotMarkMessageAsProcessedWhenIbanDiffers() {
      // given - existing payment with different IBAN but same description
      var existingPayment =
          paymentMatchingXmlTemplate().externalId(null).remitterIban("EE999DIFFERENT").build();
      repository.savePaymentData(existingPayment);

      // when - XML arrives with EE157700771001802057
      var messageId = processXmlMessage(XML_TEMPLATE);

      // then - message should not be marked as processed due to IBAN mismatch
      var message = swedbankMessageRepository.findById(messageId).orElseThrow();
      assertThat(message.getProcessedAt()).isNull();
    }

    @Test
    void doesNotMatchWhenDescriptionDiffers() {
      // given - existing payment with different description
      var existingPayment =
          paymentMatchingXmlTemplate()
              .externalId(null)
              .description("Different description")
              .build();
      repository.savePaymentData(existingPayment);

      // when - XML arrives with "Test payment"
      processXmlMessage(XML_TEMPLATE);

      // then - new payment is created
      assertThat(repository.findAll().size()).isEqualTo(2);
    }

    @Test
    void doesNotMatchWhenPaymentIsTooOld() {
      // TODO: add test here where we make sure that an old entry does not get picked up.
      // Not trivial as created_at is set on database layer
    }

    @Test
    void doesNotMatchWhenExistingHasExternalId() {
      // given - existing payment with externalId already set (from previous bank statement)
      var existingPayment =
          paymentMatchingXmlTemplate()
              .externalId("existing-ext-id")
              .receivedBefore(Instant.parse("2025-09-30T10:00:00Z"))
              .build();
      repository.savePaymentData(existingPayment);

      // when - XML arrives
      processXmlMessage(XML_TEMPLATE);

      // then - new payment is created (existing one cannot be matched)
      assertThat(repository.findAll().size()).isEqualTo(2);
    }

    @Test
    void doesNotMarkMessageAsProcessedWhenAmountDiffers() {
      // given - existing payment with different amount but same description
      var existingPayment =
          paymentMatchingXmlTemplate().externalId(null).amount(new BigDecimal("50.00")).build();
      repository.savePaymentData(existingPayment);

      // when - XML arrives with 100.50
      var messageId = processXmlMessage(XML_TEMPLATE);

      // then - message should not be marked as processed due to amount mismatch
      var message = swedbankMessageRepository.findById(messageId).orElseThrow();
      assertThat(message.getProcessedAt()).isNull();
    }

    @Test
    void doesNotMarkMessageAsProcessedWhenNonNullFieldDiffers() {
      // given - existing payment with conflicting remitterName
      var existingPayment =
          paymentMatchingXmlTemplate().externalId(null).remitterName("Different Name").build();
      repository.savePaymentData(existingPayment);

      // when - XML arrives with different remitterName
      var messageId = processXmlMessage(XML_TEMPLATE);

      // then - message should not be marked as processed due to field mismatch
      var message = swedbankMessageRepository.findById(messageId).orElseThrow();
      assertThat(message.getProcessedAt()).isNull();
    }

    private UUID processXmlMessage(String xml) {
      var message =
          SwedbankMessage.builder()
              .requestId("test")
              .trackingId("test")
              .rawResponse(xml)
              .receivedAt(NOW)
              .build();
      var saved = swedbankMessageRepository.save(message);
      // This is the delegator from the nested class context, not from the outer class context.
      delegator.processMessages();
      return saved.getId();
    }
  }

  // Returns a builder with all fields matching XML_TEMPLATE
  private SavingFundPayment.SavingFundPaymentBuilder paymentMatchingXmlTemplate() {
    return SavingFundPayment.builder()
        .amount(new BigDecimal("100.50"))
        .currency(Currency.EUR)
        .description("Test payment")
        .remitterIban("EE157700771001802057")
        .remitterIdCode("39910273027")
        .remitterName("Jüri Tamm")
        .beneficiaryIban("EE442200221092874625")
        .beneficiaryIdCode("14118923")
        .beneficiaryName("TULEVA FONDID AS")
        .externalId("2025100112345-1")
        .receivedBefore(Instant.parse("2025-10-01T09:00:00Z"))
        .createdAt(NOW.minus(Duration.ofDays(1)));
  }

  private UUID processXmlMessage(String xml) {
    var message =
        SwedbankMessage.builder()
            .requestId("test")
            .trackingId("test")
            .rawResponse(xml)
            .receivedAt(NOW)
            .build();
    var saved = swedbankMessageRepository.save(message);
    delegator.processMessages();
    return saved.getId();
  }
}
