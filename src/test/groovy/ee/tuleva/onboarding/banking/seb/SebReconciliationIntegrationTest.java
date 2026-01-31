package ee.tuleva.onboarding.banking.seb;

import static ee.tuleva.onboarding.banking.seb.Seb.SEB_GATEWAY_TIME_ZONE;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.banking.BankType;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.ProcessBankMessagesRequested;
import ee.tuleva.onboarding.banking.message.BankingMessage;
import ee.tuleva.onboarding.banking.message.BankingMessageRepository;
import ee.tuleva.onboarding.config.TestSchedulerLockConfiguration;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import ee.tuleva.onboarding.time.ClockHolder;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@TestPropertySource(
    properties = {
      "swedbank-gateway.enabled=false",
      "seb-gateway.enabled=true",
      "seb-gateway.url=https://test.example.com",
      "seb-gateway.orgId=test-org",
      "seb-gateway.keystore.path=src/test/resources/banking/seb/test-seb-gateway.p12",
      "seb-gateway.keystore.password=testpass",
      "seb-gateway.reconciliation-delay=0s",
      "seb-gateway.accounts.DEPOSIT_EUR=EE001234567890123456",
      "seb-gateway.accounts.WITHDRAWAL_EUR=EE001234567890123457",
      "seb-gateway.accounts.FUND_INVESTMENT_EUR=EE001234567890123458"
    })
@Import({TestSchedulerLockConfiguration.class, TestSebSchedulerConfiguration.class})
@Transactional
class SebReconciliationIntegrationTest {

  @Autowired private SavingFundPaymentRepository paymentRepository;
  @Autowired private BankingMessageRepository bankingMessageRepository;
  @Autowired private ApplicationEventPublisher eventPublisher;

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
