package ee.tuleva.onboarding.banking.seb;

import static ee.tuleva.onboarding.banking.BankAccountType.FUND_INVESTMENT_EUR;
import static ee.tuleva.onboarding.banking.BankType.SEB;
import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_INVESTMENT_CASH_CLEARING;
import static ee.tuleva.onboarding.ledger.SystemAccount.INTEREST_INCOME;
import static ee.tuleva.onboarding.ledger.SystemAccount.REGISTRAR_CASH_SETTLEMENT;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.ProcessBankMessagesRequested;
import ee.tuleva.onboarding.banking.message.BankingMessage;
import ee.tuleva.onboarding.banking.message.BankingMessageRepository;
import ee.tuleva.onboarding.banking.seb.reconciliation.ReconciliationCompletedEvent;
import ee.tuleva.onboarding.ledger.FundBankLedger;
import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.ledger.SystemAccount;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@SebIntegrationTest
@RecordApplicationEvents
class PensionFundBankStatementIntegrationTest {

  private static final String REGISTRAR_IBAN = "EE001234567890123477";
  private static final String SUBFUND_ISIN = "IE00BFG1TM61";

  @Autowired private BankingMessageRepository bankingMessageRepository;
  @Autowired private SavingFundPaymentRepository savingFundPaymentRepository;
  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private ApplicationEvents applicationEvents;
  @Autowired private LedgerService ledgerService;
  @Autowired private NavLedgerRepository navLedgerRepository;
  @Autowired private FundBankLedger fundBankLedger;
  @Autowired private BankAccounts bankAccounts;

  @DynamicPropertySource
  static void registrarIbans(DynamicPropertyRegistry registry) {
    registry.add("seb-gateway.registrar-ibans", () -> REGISTRAR_IBAN);
  }

  @Test
  void pensionFundStatementLandsAsBalancedFundScopedLedgerTransactions() {
    persistMessage(pensionFundStatement());

    eventPublisher.publishEvent(new ProcessBankMessagesRequested());

    assertThat(
            savingFundPaymentRepository.findAll().stream()
                .filter(payment -> String.valueOf(payment.getExternalId()).startsWith("PENSION-")))
        .isEmpty();
    assertThat(balance(FUND_INVESTMENT_CASH_CLEARING, TUK75))
        .isEqualByComparingTo(new BigDecimal("50997.00"));
    assertThat(balance(REGISTRAR_CASH_SETTLEMENT, TUK75))
        .isEqualByComparingTo(new BigDecimal("-1000000.00"));
    assertThat(balance(SystemAccount.BANK_FEE, TUK75)).isEqualByComparingTo(new BigDecimal("5.00"));
    assertThat(balance(INTEREST_INCOME, TUK75)).isEqualByComparingTo(new BigDecimal("-2.00"));
    assertThat(instrumentBalance(SystemAccount.SECURITIES_CUSTODY, TUK75))
        .isEqualByComparingTo(new BigDecimal("27549.75"));
    assertThat(instrumentBalance(SystemAccount.TRADE_CASH_SETTLEMENT, TUK75))
        .isEqualByComparingTo(new BigDecimal("949000.00"));
    assertThat(fundBankLedger.countUnresolvedUnclassifiedEntries(TUK75)).isZero();
    assertThat(balance(FUND_INVESTMENT_CASH_CLEARING, TKF100))
        .isEqualByComparingTo(BigDecimal.ZERO);

    assertThat(
            applicationEvents.stream(ReconciliationCompletedEvent.class)
                .filter(event -> event.bankAccount().fund() == TUK75)
                .filter(ReconciliationCompletedEvent::matched))
        .hasSize(1);

    persistMessage(pensionFundStatement());
    eventPublisher.publishEvent(new ProcessBankMessagesRequested());

    assertThat(balance(FUND_INVESTMENT_CASH_CLEARING, TUK75))
        .isEqualByComparingTo(new BigDecimal("50997.00"));
    assertThat(instrumentBalance(SystemAccount.SECURITIES_CUSTODY, TUK75))
        .isEqualByComparingTo(new BigDecimal("27549.75"));
  }

  @Test
  void firstStatementWithANonZeroOpeningBalanceSeedsTheLedgerAndReconciles() {
    persistMessage(establishedAccountStatement());

    eventPublisher.publishEvent(new ProcessBankMessagesRequested());

    assertThat(balance(FUND_INVESTMENT_CASH_CLEARING, TUV100))
        .isEqualByComparingTo(new BigDecimal("250002.00"));
    assertThat(balance(REGISTRAR_CASH_SETTLEMENT, TUV100))
        .isEqualByComparingTo(new BigDecimal("-250000.00"));
    assertThat(
            applicationEvents.stream(ReconciliationCompletedEvent.class)
                .filter(event -> event.bankAccount().fund() == TUV100)
                .filter(ReconciliationCompletedEvent::matched))
        .hasSize(1);

    persistMessage(establishedAccountStatement());
    eventPublisher.publishEvent(new ProcessBankMessagesRequested());

    assertThat(balance(FUND_INVESTMENT_CASH_CLEARING, TUV100))
        .isEqualByComparingTo(new BigDecimal("250002.00"));
  }

  private BigDecimal balance(SystemAccount systemAccount, TulevaFund fund) {
    return ledgerService.getSystemAccount(systemAccount, fund).getBalance();
  }

  private BigDecimal instrumentBalance(SystemAccount systemAccount, TulevaFund fund) {
    return navLedgerRepository.getSystemAccountBalance(
        systemAccount.getAccountName(fund, SUBFUND_ISIN));
  }

  private void persistMessage(String xml) {
    var messageId = UUID.randomUUID().toString();
    bankingMessageRepository.save(
        BankingMessage.builder()
            .bankType(SEB)
            .requestId(messageId)
            .trackingId(messageId)
            .rawResponse(xml)
            .timezone("Europe/Tallinn")
            .build());
  }

  private String pensionFundStatement() {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.053.001.02">
          <BkToCstmrStmt>
            <GrpHdr>
              <MsgId>test-pension-statement</MsgId>
              <CreDtTm>2026-02-11T01:00:00</CreDtTm>
            </GrpHdr>
            <Stmt>
              <Id>test-stmt-pension</Id>
              <CreDtTm>2026-02-11T01:00:00</CreDtTm>
              <FrToDt>
                <FrDtTm>2026-02-10T00:00:00</FrDtTm>
                <ToDtTm>2026-02-10T23:59:59</ToDtTm>
              </FrToDt>
              <Acct>
                <Id>
                  <IBAN>%1$s</IBAN>
                </Id>
                <Ccy>EUR</Ccy>
                <Ownr>
                  <Nm>TULEVA MAAILMA AKTSIATE PENSIONIFOND</Nm>
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
                  <Dt>2026-02-10</Dt>
                </Dt>
              </Bal>
              <Bal>
                <Tp>
                  <CdOrPrtry>
                    <Cd>CLBD</Cd>
                  </CdOrPrtry>
                </Tp>
                <Amt Ccy="EUR">50997.00</Amt>
                <CdtDbtInd>CRDT</CdtDbtInd>
                <Dt>
                  <Dt>2026-02-10</Dt>
                </Dt>
              </Bal>
              <Ntry>
                <NtryRef>PENSION-1</NtryRef>
                <Amt Ccy="EUR">1000000.00</Amt>
                <CdtDbtInd>CRDT</CdtDbtInd>
                <Sts>BOOK</Sts>
                <BookgDt>
                  <Dt>2026-02-10</Dt>
                </BookgDt>
                <ValDt>
                  <Dt>2026-02-10</Dt>
                </ValDt>
                <BkTxCd>
                  <Domn>
                    <Cd>PMNT</Cd>
                    <Fmly>
                      <Cd>RCDT</Cd>
                      <SubFmlyCd>ESCT</SubFmlyCd>
                    </Fmly>
                  </Domn>
                </BkTxCd>
                <NtryDtls>
                  <TxDtls>
                    <Refs>
                      <AcctSvcrRef>PENSION-1</AcctSvcrRef>
                    </Refs>
                    <RltdPties>
                      <Dbtr>
                        <Nm>AS Pensionikeskus</Nm>
                      </Dbtr>
                      <DbtrAcct>
                        <Id>
                          <IBAN>%2$s</IBAN>
                        </Id>
                      </DbtrAcct>
                    </RltdPties>
                    <RmtInf>
                      <Ustrd>Osakute laekumine</Ustrd>
                    </RmtInf>
                  </TxDtls>
                </NtryDtls>
              </Ntry>
              <Ntry>
                <NtryRef>PENSION-2</NtryRef>
                <Amt Ccy="EUR">999000.00</Amt>
                <CdtDbtInd>DBIT</CdtDbtInd>
                <Sts>BOOK</Sts>
                <BookgDt>
                  <Dt>2026-02-10</Dt>
                </BookgDt>
                <ValDt>
                  <Dt>2026-02-10</Dt>
                </ValDt>
                <BkTxCd>
                  <Domn>
                    <Cd>SECU</Cd>
                    <Fmly>
                      <Cd>SETT</Cd>
                      <SubFmlyCd>SUBS</SubFmlyCd>
                    </Fmly>
                  </Domn>
                </BkTxCd>
                <NtryDtls>
                  <TxDtls>
                    <Refs>
                      <AcctSvcrRef>PENSION-2</AcctSvcrRef>
                    </Refs>
                    <RmtInf>
                      <Ustrd>DLA0544429/BDWTEIA/29000/34.448275862/Buy/ BlackRock Asset Management Ireland Ltd</Ustrd>
                    </RmtInf>
                  </TxDtls>
                </NtryDtls>
              </Ntry>
              <Ntry>
                <NtryRef>PENSION-3</NtryRef>
                <Amt Ccy="EUR">50000.00</Amt>
                <CdtDbtInd>CRDT</CdtDbtInd>
                <Sts>BOOK</Sts>
                <BookgDt>
                  <Dt>2026-02-10</Dt>
                </BookgDt>
                <ValDt>
                  <Dt>2026-02-10</Dt>
                </ValDt>
                <BkTxCd>
                  <Domn>
                    <Cd>SECU</Cd>
                    <Fmly>
                      <Cd>SETT</Cd>
                      <SubFmlyCd>TRAD</SubFmlyCd>
                    </Fmly>
                  </Domn>
                </BkTxCd>
                <NtryDtls>
                  <TxDtls>
                    <Refs>
                      <AcctSvcrRef>PENSION-3</AcctSvcrRef>
                    </Refs>
                    <RmtInf>
                      <Ustrd>DLA0553691/BDWTEIA/1450.25/34.477/Sell/ BlackRock Asset Management Ireland Ltd</Ustrd>
                    </RmtInf>
                  </TxDtls>
                </NtryDtls>
              </Ntry>
              <Ntry>
                <NtryRef>PENSION-4</NtryRef>
                <Amt Ccy="EUR">5.00</Amt>
                <CdtDbtInd>DBIT</CdtDbtInd>
                <Sts>BOOK</Sts>
                <BookgDt>
                  <Dt>2026-02-10</Dt>
                </BookgDt>
                <ValDt>
                  <Dt>2026-02-10</Dt>
                </ValDt>
                <BkTxCd>
                  <Domn>
                    <Cd>ACMT</Cd>
                    <Fmly>
                      <Cd>MDOP</Cd>
                      <SubFmlyCd>FEES</SubFmlyCd>
                    </Fmly>
                  </Domn>
                </BkTxCd>
                <NtryDtls>
                  <TxDtls>
                    <Refs>
                      <AcctSvcrRef>PENSION-4</AcctSvcrRef>
                    </Refs>
                    <RmtInf>
                      <Ustrd>Konto kuutasu, veebruar 2026</Ustrd>
                    </RmtInf>
                  </TxDtls>
                </NtryDtls>
              </Ntry>
              <Ntry>
                <NtryRef>PENSION-5</NtryRef>
                <Amt Ccy="EUR">2.00</Amt>
                <CdtDbtInd>CRDT</CdtDbtInd>
                <Sts>BOOK</Sts>
                <BookgDt>
                  <Dt>2026-02-10</Dt>
                </BookgDt>
                <ValDt>
                  <Dt>2026-02-10</Dt>
                </ValDt>
                <BkTxCd>
                  <Domn>
                    <Cd>ACMT</Cd>
                    <Fmly>
                      <Cd>MCOP</Cd>
                      <SubFmlyCd>INTR</SubFmlyCd>
                    </Fmly>
                  </Domn>
                </BkTxCd>
                <NtryDtls>
                  <TxDtls>
                    <Refs>
                      <AcctSvcrRef>PENSION-5</AcctSvcrRef>
                    </Refs>
                    <RmtInf>
                      <Ustrd>Intress</Ustrd>
                    </RmtInf>
                  </TxDtls>
                </NtryDtls>
              </Ntry>
            </Stmt>
          </BkToCstmrStmt>
        </Document>
        """
        .formatted(bankAccounts.getIban(TUK75, FUND_INVESTMENT_EUR), REGISTRAR_IBAN);
  }

  private String establishedAccountStatement() {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.053.001.02">
          <BkToCstmrStmt>
            <GrpHdr>
              <MsgId>test-pension-opening-balance</MsgId>
              <CreDtTm>2026-02-11T01:00:00</CreDtTm>
            </GrpHdr>
            <Stmt>
              <Id>test-stmt-opening-balance</Id>
              <CreDtTm>2026-02-11T01:00:00</CreDtTm>
              <FrToDt>
                <FrDtTm>2026-02-10T00:00:00</FrDtTm>
                <ToDtTm>2026-02-10T23:59:59</ToDtTm>
              </FrToDt>
              <Acct>
                <Id>
                  <IBAN>%1$s</IBAN>
                </Id>
                <Ccy>EUR</Ccy>
                <Ownr>
                  <Nm>TULEVA MAAILMA AKTSIATE OSAKUD 100 PENSIONIFOND</Nm>
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
                <Amt Ccy="EUR">250000.00</Amt>
                <CdtDbtInd>CRDT</CdtDbtInd>
                <Dt>
                  <Dt>2026-02-10</Dt>
                </Dt>
              </Bal>
              <Bal>
                <Tp>
                  <CdOrPrtry>
                    <Cd>CLBD</Cd>
                  </CdOrPrtry>
                </Tp>
                <Amt Ccy="EUR">250002.00</Amt>
                <CdtDbtInd>CRDT</CdtDbtInd>
                <Dt>
                  <Dt>2026-02-10</Dt>
                </Dt>
              </Bal>
              <Ntry>
                <NtryRef>PENSION-OB-1</NtryRef>
                <Amt Ccy="EUR">2.00</Amt>
                <CdtDbtInd>CRDT</CdtDbtInd>
                <Sts>BOOK</Sts>
                <BookgDt>
                  <Dt>2026-02-10</Dt>
                </BookgDt>
                <ValDt>
                  <Dt>2026-02-10</Dt>
                </ValDt>
                <BkTxCd>
                  <Domn>
                    <Cd>ACMT</Cd>
                    <Fmly>
                      <Cd>MCOP</Cd>
                      <SubFmlyCd>INTR</SubFmlyCd>
                    </Fmly>
                  </Domn>
                </BkTxCd>
                <NtryDtls>
                  <TxDtls>
                    <Refs>
                      <AcctSvcrRef>PENSION-OB-1</AcctSvcrRef>
                    </Refs>
                    <RmtInf>
                      <Ustrd>Intress</Ustrd>
                    </RmtInf>
                  </TxDtls>
                </NtryDtls>
              </Ntry>
            </Stmt>
          </BkToCstmrStmt>
        </Document>
        """
        .formatted(bankAccounts.getIban(TUV100, FUND_INVESTMENT_EUR));
  }
}
