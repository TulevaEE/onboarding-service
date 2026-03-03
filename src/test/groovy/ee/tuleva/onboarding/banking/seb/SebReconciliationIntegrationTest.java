package ee.tuleva.onboarding.banking.seb;

import static ee.tuleva.onboarding.banking.seb.Seb.SEB_GATEWAY_TIME_ZONE;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.banking.BankType;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.ProcessBankMessagesRequested;
import ee.tuleva.onboarding.banking.message.BankingMessage;
import ee.tuleva.onboarding.banking.message.BankingMessageRepository;
import ee.tuleva.onboarding.banking.seb.reconciliation.ReconciliationCompletedEvent;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.ledger.SystemAccount;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import ee.tuleva.onboarding.time.ClockHolder;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@SebIntegrationTest
@RecordApplicationEvents
class SebReconciliationIntegrationTest {

  @Autowired private SavingFundPaymentRepository paymentRepository;
  @Autowired private BankingMessageRepository bankingMessageRepository;
  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private SavingsFundLedger savingsFundLedger;
  @Autowired private LedgerService ledgerService;

  private static final Instant NOW = Instant.parse("2025-10-01T12:00:00Z");

  @BeforeEach
  void setUp() {
    ClockHolder.setClock(Clock.fixed(NOW, ZoneId.of("UTC")));
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void paymentsAreStoredEvenWhenReconciliationFails() {
    // Given - a HISTORIC_STATEMENT (camt.053) with payments and a closing balance
    // The closing balance (1000.00) will NOT match the ledger balance (0.00)
    // This will cause reconciliation to fail
    var xml = createHistoricStatementWithMismatchedBalance();
    persistMessage(xml);

    // When - process the message
    eventPublisher.publishEvent(new ProcessBankMessagesRequested());

    // Then - payments should be stored despite reconciliation failure
    var payments = paymentRepository.findAll();
    assertThat(payments).as("Payments should be stored even when reconciliation fails").hasSize(1);

    var payment = payments.getFirst();
    assertThat(payment.getExternalId()).isEqualTo("2025100112345-1");
    assertThat(payment.getDescription()).isEqualTo("Test payment");
  }

  @Test
  void fundInvestmentAccount_managementFeePayment_reconcilesSuccessfully() {
    savingsFundLedger.transferToFundAccount(new BigDecimal("742.34"), randomUUID());
    var xml = createFundInvestmentStatementWithManagementFee();
    persistMessage(xml);

    eventPublisher.publishEvent(new ProcessBankMessagesRequested());

    var ledgerBalance =
        ledgerService
            .getSystemAccount(SystemAccount.FUND_INVESTMENT_CASH_CLEARING, TulevaFund.TKF100)
            .getBalance();
    assertThat(ledgerBalance).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Autowired private ApplicationEvents applicationEvents;

  @Test
  void reconciliation_shouldIncludePaymentVerificationEntriesCreatedAfterMidnight() {
    // Given - bank statement for Oct 1 with an incoming payment of 100.00
    // Processing happens after midnight EET on Oct 2 (nightly batch)
    ClockHolder.setClock(
        Clock.fixed(Instant.parse("2025-10-01T22:00:00Z"), ZoneId.of("UTC"))); // = Oct 2 01:00
    // EEST

    // Simulate PaymentVerificationJob creating a ledger entry with Oct 1 booking date
    // In production, PaymentVerificationService extracts this from payment.receivedBefore
    savingsFundLedger.recordUnattributedPayment(
        new BigDecimal("100.00"), randomUUID(), LocalDate.of(2025, 10, 1));

    // Bank statement for Oct 1 with closing balance = 100.00
    var xml = createDepositStatementWithClosingBalance("100.00", "2025-10-01");
    persistMessage(xml);

    // When
    eventPublisher.publishEvent(new ProcessBankMessagesRequested());

    // Then — reconciliation should succeed (ledger includes the payment verification entry)
    assertThat(
            applicationEvents.stream(ReconciliationCompletedEvent.class)
                .anyMatch(ReconciliationCompletedEvent::matched))
        .isTrue();
  }

  @Test
  void reconciliation_shouldIgnoreLedgerEntriesAfterBankStatementDate() {
    // Given - a ledger entry on Oct 1 (bank statement date)
    ClockHolder.setClock(Clock.fixed(Instant.parse("2025-10-01T12:00:00Z"), ZoneId.of("UTC")));
    savingsFundLedger.transferToFundAccount(new BigDecimal("1000.00"), randomUUID());

    // Advance clock to Oct 2 (next day) — simulating re-import of Oct 1 EOD on Oct 2
    ClockHolder.setClock(Clock.fixed(Instant.parse("2025-10-02T12:00:00Z"), ZoneId.of("UTC")));

    // Create an intraday entry that lands on Oct 2
    savingsFundLedger.recordInterestReceived(
        new BigDecimal("50.00"), randomUUID(), SystemAccount.FUND_INVESTMENT_CASH_CLEARING);

    // Bank statement for Oct 1 with closing balance = 1000.00 (no change)
    var xml = createFundInvestmentStatementWithClosingBalance("1000.00", "2025-10-01");
    persistMessage(xml);

    // When
    eventPublisher.publishEvent(new ProcessBankMessagesRequested());

    // Then — reconciliation should succeed (ledger at Oct 1 = 1000.00)
    assertThat(
            applicationEvents.stream(ReconciliationCompletedEvent.class)
                .anyMatch(ReconciliationCompletedEvent::matched))
        .isTrue();
  }

  private String createDepositStatementWithClosingBalance(
      String closingBalance, String balanceDate) {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.053.001.02">
          <BkToCstmrStmt>
            <GrpHdr>
              <MsgId>test-deposit-recon</MsgId>
              <CreDtTm>2025-10-02T01:00:00</CreDtTm>
            </GrpHdr>
            <Stmt>
              <Id>test-stmt-deposit</Id>
              <CreDtTm>2025-10-02T01:00:00</CreDtTm>
              <FrToDt>
                <FrDtTm>%2$s</FrDtTm>
                <ToDtTm>%2$s</ToDtTm>
              </FrToDt>
              <Acct>
                <Id>
                  <IBAN>EE001234567890123456</IBAN>
                </Id>
                <Ccy>EUR</Ccy>
                <Ownr>
                  <Nm>TULEVA FONDID AS</Nm>
                  <Id>
                    <OrgId>
                      <Othr>
                        <Id>14118923</Id>
                      </Othr>
                    </OrgId>
                  </Id>
                </Ownr>
              </Acct>
              <Bal>
                <Tp>
                  <CdOrPrtry>
                    <Cd>OPBD</Cd>
                  </CdOrPrtry>
                </Tp>
                <Amt Ccy="EUR">0.00</Amt>
                <CdtDbtInd>CRDT</CdtDbtInd>
                <Dt>
                  <Dt>%2$s</Dt>
                </Dt>
              </Bal>
              <Bal>
                <Tp>
                  <CdOrPrtry>
                    <Cd>CLBD</Cd>
                  </CdOrPrtry>
                </Tp>
                <Amt Ccy="EUR">%1$s</Amt>
                <CdtDbtInd>CRDT</CdtDbtInd>
                <Dt>
                  <Dt>%2$s</Dt>
                </Dt>
              </Bal>
            </Stmt>
          </BkToCstmrStmt>
        </Document>
        """
        .formatted(closingBalance, balanceDate);
  }

  private String createFundInvestmentStatementWithClosingBalance(
      String closingBalance, String balanceDate) {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.053.001.02">
          <BkToCstmrStmt>
            <GrpHdr>
              <MsgId>test-recon-timing</MsgId>
              <CreDtTm>2025-10-02T12:00:00</CreDtTm>
            </GrpHdr>
            <Stmt>
              <Id>test-stmt-timing</Id>
              <CreDtTm>2025-10-02T12:00:00</CreDtTm>
              <FrToDt>
                <FrDtTm>%2$s</FrDtTm>
                <ToDtTm>%2$s</ToDtTm>
              </FrToDt>
              <Acct>
                <Id>
                  <IBAN>EE001234567890123458</IBAN>
                </Id>
                <Ccy>EUR</Ccy>
                <Ownr>
                  <Nm>TULEVA FONDID AS</Nm>
                  <Id>
                    <OrgId>
                      <Othr>
                        <Id>14118923</Id>
                      </Othr>
                    </OrgId>
                  </Id>
                </Ownr>
              </Acct>
              <Bal>
                <Tp>
                  <CdOrPrtry>
                    <Cd>OPBD</Cd>
                  </CdOrPrtry>
                </Tp>
                <Amt Ccy="EUR">%1$s</Amt>
                <CdtDbtInd>CRDT</CdtDbtInd>
                <Dt>
                  <Dt>%2$s</Dt>
                </Dt>
              </Bal>
              <Bal>
                <Tp>
                  <CdOrPrtry>
                    <Cd>CLBD</Cd>
                  </CdOrPrtry>
                </Tp>
                <Amt Ccy="EUR">%1$s</Amt>
                <CdtDbtInd>CRDT</CdtDbtInd>
                <Dt>
                  <Dt>%2$s</Dt>
                </Dt>
              </Bal>
            </Stmt>
          </BkToCstmrStmt>
        </Document>
        """
        .formatted(closingBalance, balanceDate);
  }

  private String createFundInvestmentStatementWithManagementFee() {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.053.001.02">
          <BkToCstmrStmt>
            <GrpHdr>
              <MsgId>test-mgmt-fee</MsgId>
              <CreDtTm>2025-10-01T12:00:00</CreDtTm>
            </GrpHdr>
            <Stmt>
              <Id>test-stmt-mgmt-fee</Id>
              <CreDtTm>2025-10-01T12:00:00</CreDtTm>
              <FrToDt>
                <FrDtTm>2025-10-01T00:00:00</FrDtTm>
                <ToDtTm>2025-10-01T23:59:59</ToDtTm>
              </FrToDt>
              <Acct>
                <Id>
                  <IBAN>EE001234567890123458</IBAN>
                </Id>
                <Ccy>EUR</Ccy>
                <Ownr>
                  <Nm>TULEVA FONDID AS</Nm>
                  <Id>
                    <OrgId>
                      <Othr>
                        <Id>14118923</Id>
                      </Othr>
                    </OrgId>
                  </Id>
                </Ownr>
              </Acct>
              <Bal>
                <Tp>
                  <CdOrPrtry>
                    <Cd>OPBD</Cd>
                  </CdOrPrtry>
                </Tp>
                <Amt Ccy="EUR">742.34</Amt>
                <CdtDbtInd>CRDT</CdtDbtInd>
                <Dt>
                  <Dt>2025-10-01</Dt>
                </Dt>
              </Bal>
              <Bal>
                <Tp>
                  <CdOrPrtry>
                    <Cd>CLBD</Cd>
                  </CdOrPrtry>
                </Tp>
                <Amt Ccy="EUR">0.00</Amt>
                <CdtDbtInd>CRDT</CdtDbtInd>
                <Dt>
                  <Dt>2025-10-01</Dt>
                </Dt>
              </Bal>
              <Ntry>
                <NtryRef>2025100199999-1</NtryRef>
                <Amt Ccy="EUR">742.34</Amt>
                <CdtDbtInd>DBIT</CdtDbtInd>
                <Sts>BOOK</Sts>
                <BookgDt>
                  <Dt>2025-10-01</Dt>
                </BookgDt>
                <ValDt>
                  <Dt>2025-10-01</Dt>
                </ValDt>
                <AcctSvcrRef>2025100199999-1</AcctSvcrRef>
                <NtryDtls>
                  <TxDtls>
                    <Refs>
                      <AcctSvcrRef>2025100199999-1</AcctSvcrRef>
                    </Refs>
                    <AmtDtls>
                      <TxAmt>
                        <Amt Ccy="EUR">742.34</Amt>
                      </TxAmt>
                    </AmtDtls>
                    <RltdPties>
                      <Cdtr>
                        <Nm>Tuleva Fondid AS</Nm>
                      </Cdtr>
                      <CdtrAcct>
                        <Id>
                          <IBAN>EE721010220306590220</IBAN>
                        </Id>
                      </CdtrAcct>
                    </RltdPties>
                    <RmtInf>
                      <Ustrd>Valitsemistasu 02.-28.02.26</Ustrd>
                    </RmtInf>
                  </TxDtls>
                </NtryDtls>
              </Ntry>
            </Stmt>
          </BkToCstmrStmt>
        </Document>
        """;
  }

  private String createHistoricStatementWithMismatchedBalance() {
    // HISTORIC_STATEMENT uses camt.053 format with BkToCstmrStmt
    // The closing balance (CLBD) is set to 1000.00, but ledger will have 0.00
    // This mismatch will cause reconciliation to fail
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.053.001.02">
          <BkToCstmrStmt>
            <GrpHdr>
              <MsgId>test-reconciliation</MsgId>
              <CreDtTm>2025-10-01T12:00:00</CreDtTm>
            </GrpHdr>
            <Stmt>
              <Id>test-stmt-1</Id>
              <CreDtTm>2025-10-01T12:00:00</CreDtTm>
              <FrToDt>
                <FrDtTm>2025-10-01T00:00:00</FrDtTm>
                <ToDtTm>2025-10-01T23:59:59</ToDtTm>
              </FrToDt>
              <Acct>
                <Id>
                  <IBAN>EE001234567890123456</IBAN>
                </Id>
                <Ccy>EUR</Ccy>
                <Ownr>
                  <Nm>TULEVA FONDID AS</Nm>
                  <Id>
                    <OrgId>
                      <Othr>
                        <Id>14118923</Id>
                      </Othr>
                    </OrgId>
                  </Id>
                </Ownr>
              </Acct>
              <Bal>
                <Tp>
                  <CdOrPrtry>
                    <Cd>OPBD</Cd>
                  </CdOrPrtry>
                </Tp>
                <Amt Ccy="EUR">900.00</Amt>
                <CdtDbtInd>CRDT</CdtDbtInd>
                <Dt>
                  <Dt>2025-10-01</Dt>
                </Dt>
              </Bal>
              <Bal>
                <Tp>
                  <CdOrPrtry>
                    <Cd>CLBD</Cd>
                  </CdOrPrtry>
                </Tp>
                <Amt Ccy="EUR">1000.00</Amt>
                <CdtDbtInd>CRDT</CdtDbtInd>
                <Dt>
                  <Dt>2025-10-01</Dt>
                </Dt>
              </Bal>
              <Ntry>
                <NtryRef>2025100112345-1</NtryRef>
                <Amt Ccy="EUR">100.00</Amt>
                <CdtDbtInd>CRDT</CdtDbtInd>
                <Sts>BOOK</Sts>
                <BookgDt>
                  <Dt>2025-10-01</Dt>
                </BookgDt>
                <ValDt>
                  <Dt>2025-10-01</Dt>
                </ValDt>
                <AcctSvcrRef>2025100112345-1</AcctSvcrRef>
                <NtryDtls>
                  <TxDtls>
                    <Refs>
                      <AcctSvcrRef>2025100112345-1</AcctSvcrRef>
                    </Refs>
                    <AmtDtls>
                      <TxAmt>
                        <Amt Ccy="EUR">100.00</Amt>
                      </TxAmt>
                    </AmtDtls>
                    <RltdPties>
                      <Dbtr>
                        <Nm>Jüri Tamm</Nm>
                        <Id>
                          <PrvtId>
                            <Othr>
                              <Id>39910273027</Id>
                              <SchmeNm>
                                <Cd>NIDN</Cd>
                              </SchmeNm>
                            </Othr>
                          </PrvtId>
                        </Id>
                      </Dbtr>
                      <DbtrAcct>
                        <Id>
                          <IBAN>EE157700771001802057</IBAN>
                        </Id>
                      </DbtrAcct>
                    </RltdPties>
                    <RmtInf>
                      <Ustrd>Test payment</Ustrd>
                    </RmtInf>
                  </TxDtls>
                </NtryDtls>
              </Ntry>
            </Stmt>
          </BkToCstmrStmt>
        </Document>
        """;
  }

  private void persistMessage(String xml) {
    var message =
        BankingMessage.builder()
            .bankType(BankType.SEB)
            .requestId("test-reconciliation")
            .trackingId("test-reconciliation")
            .rawResponse(xml)
            .timezone(SEB_GATEWAY_TIME_ZONE.getId())
            .receivedAt(NOW)
            .build();
    bankingMessageRepository.save(message);
  }
}
