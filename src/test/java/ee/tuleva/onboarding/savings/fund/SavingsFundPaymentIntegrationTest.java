package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.ledger.SystemAccount.*;
import static ee.tuleva.onboarding.ledger.UserAccount.*;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.*;
import static ee.tuleva.onboarding.swedbank.statement.BankAccountType.FUND_INVESTMENT_EUR;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.ledger.LedgerAccount;
import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.savings.fund.issuing.FundAccountPaymentJob;
import ee.tuleva.onboarding.savings.fund.issuing.IssuingJob;
import ee.tuleva.onboarding.swedbank.fetcher.SwedbankAccountConfiguration;
import ee.tuleva.onboarding.swedbank.fetcher.SwedbankMessage;
import ee.tuleva.onboarding.swedbank.fetcher.SwedbankMessageRepository;
import ee.tuleva.onboarding.swedbank.processor.SwedbankMessageDelegator;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SavingsFundPaymentIntegrationTest {

  @Autowired private SavingFundPaymentRepository paymentRepository;
  @Autowired private SwedbankMessageRepository swedbankMessageRepository;
  @Autowired private SwedbankMessageDelegator swedbankMessageDelegator;
  @Autowired private PaymentVerificationJob paymentVerificationJob;
  @Autowired private SavingsFundReservationJob savingsFundReservationJob;
  @Autowired private IssuingJob issuingJob;
  @Autowired private FundAccountPaymentJob fundAccountPaymentJob;
  @Autowired private UserRepository userRepository;
  @Autowired private LedgerService ledgerService;
  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;
  @Autowired private SwedbankAccountConfiguration swedbankAccountConfiguration;

  // Monday 2025-09-29 17:00 EET (15:00 UTC) - after 16:00 cutoff
  private static final Instant NOW = Instant.parse("2025-09-29T15:00:00Z");

  private User testUser;

  @BeforeEach
  void setUp() {
    ClockHolder.setClock(Clock.fixed(NOW, ZoneId.of("UTC")));

    // Create user and complete savings fund onboarding
    testUser =
        userRepository.save(
            User.builder()
                .firstName("Jüri")
                .lastName("Tamm")
                .personalCode("39910273027")
                .email("juri.tamm@example.com")
                .phoneNumber("+372 5555 5555")
                .build());

    // Mark user as onboarded to savings fund
    jdbcTemplate.update(
        "insert into savings_fund_onboarding (user_id) values (:user_id)",
        Map.of("user_id", testUser.getId()));

    // Set up ledger for the user
    ledgerService.onboard(testUser);
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  @DisplayName("End-to-end happy path: XML → RECEIVED → VERIFIED → RESERVED → ISSUED → PROCESSED")
  void endToEndHappyPath() {
    // Given - XML message with payment from 3 working days ago
    var xml = createHappyPathXml();
    persistXmlMessage(xml, NOW);

    // Step 1: Process XML message → Payment should be RECEIVED
    swedbankMessageDelegator.processMessages();

    // Build expected payment
    var expectedPayment =
        SavingFundPayment.builder()
            .amount(new BigDecimal("100.50"))
            .currency(Currency.EUR)
            .status(RECEIVED)
            .remitterName("Jüri Tamm")
            .remitterIdCode("39910273027")
            .remitterIban("EE157700771001802057")
            .beneficiaryIban("EE442200221092874625")
            .description("Test payment 39910273027")
            .externalId("2025092412345-1")
            .build();

    var payments = paymentRepository.findAll();

    assertThat(payments)
        .usingRecursiveFieldByFieldElementComparatorOnFields(
            "amount",
            "currency",
            "status",
            "remitterName",
            "remitterIdCode",
            "remitterIban",
            "beneficiaryIban",
            "description",
            "externalId")
        .containsExactly(expectedPayment);

    var payment = payments.getFirst();
    assertThat(payment.getUserId()).as("Payment should not be attached to any user yet").isNull();
    var paymentId = payment.getId();

    // Step 2: Run verification job → Payment should be VERIFIED and attached to user
    paymentVerificationJob.runJob();

    payment = paymentRepository.findById(paymentId).orElseThrow();
    assertThat(payment.getStatus()).isEqualTo(VERIFIED);
    assertThat(payment.getUserId()).isEqualTo(testUser.getId());

    // Assert ledger: user cash liability increased, incoming payments clearing increased
    var paymentAmount = new BigDecimal("100.50");
    assertThat(getUserCashAccount().getBalance()).isEqualByComparingTo(paymentAmount.negate());
    assertThat(getIncomingPaymentsClearingAccount().getBalance())
        .isEqualByComparingTo(paymentAmount);

    // Step 3: Run reservation job → Payment should be RESERVED
    savingsFundReservationJob.runJob();

    payment = paymentRepository.findById(paymentId).orElseThrow();
    assertThat(payment.getStatus()).isEqualTo(RESERVED);

    // Assert ledger: cash moved to cash_reserved
    assertThat(getUserCashAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getUserCashReservedAccount().getBalance())
        .isEqualByComparingTo(paymentAmount.negate());
    assertThat(getIncomingPaymentsClearingAccount().getBalance())
        .isEqualByComparingTo(paymentAmount);

    // Step 4: Run issuing job → Payment should be ISSUED
    issuingJob.runJob();

    payment = paymentRepository.findById(paymentId).orElseThrow();
    assertThat(payment.getStatus()).isEqualTo(ISSUED);

    // Assert ledger: fund units issued from reserved cash
    var fundUnits = new BigDecimal("100.50000"); // NAV = 1.0, so units = amount
    assertThat(getUserCashAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getUserCashReservedAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getUserUnitsAccount().getBalance()).isEqualByComparingTo(fundUnits.negate());
    assertThat(getUserSubscriptionsAccount().getBalance())
        .isEqualByComparingTo(paymentAmount.negate());
    assertThat(getFundUnitsOutstandingAccount().getBalance()).isEqualByComparingTo(fundUnits);
    assertThat(getIncomingPaymentsClearingAccount().getBalance())
        .isEqualByComparingTo(paymentAmount);

    // Step 5: Run fund account payment job → Payment should be PROCESSED
    fundAccountPaymentJob.runJob();

    payment = paymentRepository.findById(paymentId).orElseThrow();
    assertThat(payment.getStatus()).isEqualTo(PROCESSED);

    var investmentIban = swedbankAccountConfiguration.getAccountIban(FUND_INVESTMENT_EUR);

    var outgoingToInvestmentXml =
        createOutgoingToInvestmentAccountXml(investmentIban, paymentAmount);
    persistXmlMessage(outgoingToInvestmentXml, NOW);
    swedbankMessageDelegator.processMessages();

    // Verify outgoing payment was created and processed
    var allPayments = paymentRepository.findAll();
    assertThat(allPayments).hasSize(2); // Original incoming + new outgoing
    var outgoingPayment =
        allPayments.stream()
            .filter(p -> p.getAmount().compareTo(ZERO) < 0)
            .findFirst()
            .orElseThrow();
    assertThat(outgoingPayment.getAmount()).isEqualByComparingTo(paymentAmount.negate());
    assertThat(outgoingPayment.getStatus()).isEqualTo(PROCESSED);
    assertThat(outgoingPayment.getBeneficiaryIban()).isEqualTo(investmentIban);

    // Assert final ledger: funds transferred from INCOMING_PAYMENTS_CLEARING to
    // FUND_INVESTMENT_CASH_CLEARING
    assertThat(getIncomingPaymentsClearingAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getFundInvestmentCashClearingAccount().getBalance())
        .isEqualByComparingTo(paymentAmount);
  }

  // XML template with a single CREDIT transaction from 3 working days ago (Wed 2025-09-24)
  private String createHappyPathXml() {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?> "
        + "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:camt.052.001.02\"> "
        + "<BkToCstmrAcctRpt> "
        + "<GrpHdr> <MsgId>test</MsgId> <CreDtTm>2025-09-24T09:00:00</CreDtTm> </GrpHdr> "
        + "<Rpt> "
        + "<Id>test-1</Id> "
        + "<CreDtTm>2025-09-24T09:00:00</CreDtTm> "
        + "<FrToDt> "
        + "<FrDtTm>2025-09-24T00:00:00</FrDtTm> "
        + "<ToDtTm>2025-09-24T09:00:00</ToDtTm> "
        + "</FrToDt> "
        + "<Acct> "
        + "<Id> <IBAN>EE442200221092874625</IBAN> </Id> "
        + "<Ownr> <Nm>TULEVA FONDID AS</Nm> "
        + "<Id> <OrgId> <Othr> <Id>14118923</Id> </Othr> </OrgId> </Id> "
        + "</Ownr> "
        + "</Acct> "
        + "<Ntry> "
        + "<NtryRef>2025092412345-1</NtryRef>"
        + "<Amt Ccy=\"EUR\">100.50</Amt> "
        + "<CdtDbtInd>CRDT</CdtDbtInd> "
        + "<Sts>BOOK</Sts> "
        + "<BookgDt> <Dt>2025-09-24</Dt> </BookgDt> "
        + "<NtryDtls> <TxDtls> "
        + "<Refs> <AcctSvcrRef>2025092412345-1</AcctSvcrRef> </Refs> "
        + "<AmtDtls> <TxAmt> <Amt Ccy=\"EUR\">100.50</Amt> </TxAmt> </AmtDtls> "
        + "<RltdPties> "
        + "<Dbtr> <Nm>Jüri Tamm</Nm> "
        + "<Id> <PrvtId> <Othr> <Id>39910273027</Id> </Othr> </PrvtId> </Id> "
        + "</Dbtr> "
        + "<DbtrAcct> <Id> <IBAN>EE157700771001802057</IBAN> </Id> </DbtrAcct> "
        + "</RltdPties> "
        + "<RmtInf> <Ustrd>Test payment 39910273027</Ustrd> </RmtInf> "
        + "</TxDtls> </NtryDtls> "
        + "</Ntry> "
        + "</Rpt> "
        + "</BkToCstmrAcctRpt> "
        + "</Document>";
  }

  private String createOutgoingToInvestmentAccountXml(String investmentIban, BigDecimal amount) {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?> "
        + "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:camt.052.001.02\"> "
        + "<BkToCstmrAcctRpt> "
        + "<GrpHdr> <MsgId>test-outgoing</MsgId> <CreDtTm>2025-09-29T15:00:00</CreDtTm> </GrpHdr> "
        + "<Rpt> "
        + "<Id>test-outgoing-1</Id> "
        + "<CreDtTm>2025-09-29T15:00:00</CreDtTm> "
        + "<FrToDt> "
        + "<FrDtTm>2025-09-29T00:00:00</FrDtTm> "
        + "<ToDtTm>2025-09-29T15:00:00</ToDtTm> "
        + "</FrToDt> "
        + "<Acct> "
        + "<Id> <IBAN>EE442200221092874625</IBAN> </Id> "
        + "<Ownr> <Nm>TULEVA FONDID AS</Nm> "
        + "<Id> <OrgId> <Othr> <Id>14118923</Id> </Othr> </OrgId> </Id> "
        + "</Ownr> "
        + "</Acct> "
        + "<Ntry> "
        + "<NtryRef>2025092915000-1</NtryRef>"
        + "<Amt Ccy=\"EUR\">"
        + amount
        + "</Amt> "
        + "<CdtDbtInd>DBIT</CdtDbtInd> "
        + "<Sts>BOOK</Sts> "
        + "<BookgDt> <Dt>2025-09-29</Dt> </BookgDt> "
        + "<NtryDtls> <TxDtls> "
        + "<Refs> <AcctSvcrRef>2025092915000-1</AcctSvcrRef> </Refs> "
        + "<AmtDtls> <TxAmt> <Amt Ccy=\"EUR\">"
        + amount
        + "</Amt> </TxAmt> </AmtDtls> "
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
  }

  private void persistXmlMessage(String xml, Instant receivedAt) {
    var message =
        SwedbankMessage.builder()
            .requestId("test-e2e")
            .trackingId("test-e2e")
            .rawResponse(xml)
            .receivedAt(receivedAt)
            .build();
    swedbankMessageRepository.save(message);
  }

  // Ledger helper methods - following pattern from SavingsFundLedgerTest
  private LedgerAccount getUserCashAccount() {
    return ledgerService.getUserAccount(testUser, CASH);
  }

  private LedgerAccount getUserCashReservedAccount() {
    return ledgerService.getUserAccount(testUser, CASH_RESERVED);
  }

  private LedgerAccount getUserUnitsAccount() {
    return ledgerService.getUserAccount(testUser, FUND_UNITS);
  }

  private LedgerAccount getUserSubscriptionsAccount() {
    return ledgerService.getUserAccount(testUser, SUBSCRIPTIONS);
  }

  private LedgerAccount getIncomingPaymentsClearingAccount() {
    return ledgerService.getSystemAccount(INCOMING_PAYMENTS_CLEARING);
  }

  private LedgerAccount getFundInvestmentCashClearingAccount() {
    return ledgerService.getSystemAccount(FUND_INVESTMENT_CASH_CLEARING);
  }

  private LedgerAccount getFundUnitsOutstandingAccount() {
    return ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING);
  }

  private LedgerAccount getUnreconciledBankReceiptsAccount() {
    return ledgerService.getSystemAccount(UNRECONCILED_BANK_RECEIPTS);
  }

  @Test
  @DisplayName(
      "Unverified payment flow: XML → RECEIVED → TO_BE_RETURNED with bounce back ledger entries")
  void unverifiedPaymentWithBounceBack() {
    // Create a user with different personal code (for mismatch scenario)
    var differentUser =
        userRepository.save(
            User.builder()
                .firstName("Maria")
                .lastName("Mets")
                .personalCode("48806046007")
                .email("maria.mets@example.com")
                .phoneNumber("+372 5555 6666")
                .build());

    // Mark this user as onboarded to savings fund
    jdbcTemplate.update(
        "insert into savings_fund_onboarding (user_id) values (:user_id)",
        Map.of("user_id", differentUser.getId()));

    // Given - XML message with payment that cannot be verified (wrong personal code)
    var xml = createUnverifiablePaymentXml();
    persistXmlMessage(xml, NOW);

    // Step 1: Process XML message → Payment should be RECEIVED
    swedbankMessageDelegator.processMessages();

    // Build expected payment
    var expectedPayment =
        SavingFundPayment.builder()
            .amount(new BigDecimal("50.00"))
            .currency(Currency.EUR)
            .status(RECEIVED)
            .remitterName("Jüri Tamm")
            .remitterIdCode("39910273027")
            .remitterIban("EE982200221234567890")
            .beneficiaryIban("EE442200221092874625")
            .description("Payment for 48806046007")
            .externalId("2025092909000-1")
            .build();

    var payments = paymentRepository.findAll();

    // Compare the actual fields we care about (ID and timestamps will be generated)
    assertThat(payments)
        .usingRecursiveFieldByFieldElementComparatorOnFields(
            "amount",
            "currency",
            "status",
            "remitterName",
            "remitterIdCode",
            "remitterIban",
            "beneficiaryIban",
            "description",
            "externalId")
        .containsExactly(expectedPayment);

    var payment = payments.getFirst();
    var paymentId = payment.getId();

    // Step 2: Run verification job → Payment should be TO_BE_RETURNED
    paymentVerificationJob.runJob();

    payment = paymentRepository.findById(paymentId).orElseThrow();
    assertThat(payment.getStatus()).isEqualTo(TO_BE_RETURNED);
    assertThat(payment.getUserId()).isNull(); // Not attached to any user
    assertThat(payment.getReturnReason())
        .isEqualTo("selgituses olev isikukood ei klapi maksja isikukoodiga");

    // Assert ledger: payment recorded as unattributed
    var paymentAmount = new BigDecimal("50.00");
    assertThat(getUnreconciledBankReceiptsAccount().getBalance())
        .isEqualByComparingTo(paymentAmount.negate());
    assertThat(getIncomingPaymentsClearingAccount().getBalance())
        .isEqualByComparingTo(paymentAmount);

    // Step 3: Process outgoing return XML → Ledger should record bounce back
    var returnXml = createReturnPaymentXml();
    persistXmlMessage(returnXml, NOW);
    swedbankMessageDelegator.processMessages();

    // Verify return payment was created
    var allPayments = paymentRepository.findAll();
    assertThat(allPayments).hasSize(2); // Original incoming + return
    var returnPayment =
        allPayments.stream()
            .filter(p -> p.getAmount().compareTo(ZERO) < 0)
            .findFirst()
            .orElseThrow();
    assertThat(returnPayment.getAmount()).isEqualByComparingTo(paymentAmount.negate());
    assertThat(returnPayment.getStatus()).isEqualTo(PROCESSED);

    // Assert final ledger: bounce back recorded, clearing accounts balanced
    assertThat(getUnreconciledBankReceiptsAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(getIncomingPaymentsClearingAccount().getBalance()).isEqualByComparingTo(ZERO);
  }

  private String createUnverifiablePaymentXml() {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?> "
        + "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:camt.052.001.02\"> "
        + "<BkToCstmrAcctRpt> "
        + "<GrpHdr> <MsgId>test-unverifiable</MsgId> <CreDtTm>2025-09-29T09:00:00</CreDtTm> </GrpHdr> "
        + "<Rpt> "
        + "<Id>test-unverifiable-1</Id> "
        + "<CreDtTm>2025-09-29T09:00:00</CreDtTm> "
        + "<FrToDt> "
        + "<FrDtTm>2025-09-29T00:00:00</FrDtTm> "
        + "<ToDtTm>2025-09-29T09:00:00</ToDtTm> "
        + "</FrToDt> "
        + "<Acct> "
        + "<Id> <IBAN>EE442200221092874625</IBAN> </Id> "
        + "<Ownr> <Nm>TULEVA FONDID AS</Nm> "
        + "<Id> <OrgId> <Othr> <Id>14118923</Id> </Othr> </OrgId> </Id> "
        + "</Ownr> "
        + "</Acct> "
        + "<Ntry> "
        + "<NtryRef>2025092909000-1</NtryRef>"
        + "<Amt Ccy=\"EUR\">50.00</Amt> "
        + "<CdtDbtInd>CRDT</CdtDbtInd> "
        + "<Sts>BOOK</Sts> "
        + "<BookgDt> <Dt>2025-09-29</Dt> </BookgDt> "
        + "<NtryDtls> <TxDtls> "
        + "<Refs> <AcctSvcrRef>2025092909000-1</AcctSvcrRef> </Refs> "
        + "<AmtDtls> <TxAmt> <Amt Ccy=\"EUR\">50.00</Amt> </TxAmt> </AmtDtls> "
        + "<RltdPties> "
        + "<Dbtr> <Nm>Jüri Tamm</Nm> "
        + "<Id> <PrvtId> <Othr> <Id>39910273027</Id> </Othr> </PrvtId> </Id> "
        + "</Dbtr> "
        + "<DbtrAcct> <Id> <IBAN>EE982200221234567890</IBAN> </Id> </DbtrAcct> "
        + "</RltdPties> "
        + "<RmtInf> <Ustrd>Payment for 48806046007</Ustrd> </RmtInf> "
        + "</TxDtls> </NtryDtls> "
        + "</Ntry> "
        + "</Rpt> "
        + "</BkToCstmrAcctRpt> "
        + "</Document>";
  }

  private String createReturnPaymentXml() {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?> "
        + "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:camt.052.001.02\"> "
        + "<BkToCstmrAcctRpt> "
        + "<GrpHdr> <MsgId>test-return</MsgId> <CreDtTm>2025-09-29T14:00:00</CreDtTm> </GrpHdr> "
        + "<Rpt> "
        + "<Id>test-return-1</Id> "
        + "<CreDtTm>2025-09-29T14:00:00</CreDtTm> "
        + "<FrToDt> "
        + "<FrDtTm>2025-09-29T00:00:00</FrDtTm> "
        + "<ToDtTm>2025-09-29T14:00:00</ToDtTm> "
        + "</FrToDt> "
        + "<Acct> "
        + "<Id> <IBAN>EE442200221092874625</IBAN> </Id> "
        + "<Ownr> <Nm>TULEVA FONDID AS</Nm> "
        + "<Id> <OrgId> <Othr> <Id>14118923</Id> </Othr> </OrgId> </Id> "
        + "</Ownr> "
        + "</Acct> "
        + "<Ntry> "
        + "<NtryRef>2025092914000-1</NtryRef>"
        + "<Amt Ccy=\"EUR\">50.00</Amt> "
        + "<CdtDbtInd>DBIT</CdtDbtInd> "
        + "<Sts>BOOK</Sts> "
        + "<BookgDt> <Dt>2025-09-29</Dt> </BookgDt> "
        + "<NtryDtls> <TxDtls> "
        + "<Refs> <AcctSvcrRef>2025092914000-1</AcctSvcrRef> </Refs> "
        + "<AmtDtls> <TxAmt> <Amt Ccy=\"EUR\">50.00</Amt> </TxAmt> </AmtDtls> "
        + "<RltdPties> "
        + "<Cdtr> <Nm>Jüri Tamm</Nm> </Cdtr> "
        + "<CdtrAcct> <Id> <IBAN>EE982200221234567890</IBAN> </Id> </CdtrAcct> "
        + "</RltdPties> "
        + "<RmtInf> <Ustrd>Tagastus: selgituses olev isikukood ei klapi maksja isikukoodiga</Ustrd> </RmtInf> "
        + "</TxDtls> </NtryDtls> "
        + "</Ntry> "
        + "</Rpt> "
        + "</BkToCstmrAcctRpt> "
        + "</Document>";
  }
}
