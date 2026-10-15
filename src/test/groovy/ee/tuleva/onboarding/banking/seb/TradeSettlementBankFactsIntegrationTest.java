package ee.tuleva.onboarding.banking.seb;

import static ee.tuleva.onboarding.banking.BankAccountType.FUND_INVESTMENT_EUR;
import static ee.tuleva.onboarding.banking.BankType.SEB;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.TRADE_SETTLEMENT;
import static ee.tuleva.onboarding.ledger.SystemAccount.SECURITIES_CUSTODY;
import static ee.tuleva.onboarding.ledger.SystemAccount.TRADE_CASH_SETTLEMENT;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.ProcessBankMessagesRequested;
import ee.tuleva.onboarding.banking.message.BankingMessage;
import ee.tuleva.onboarding.banking.message.BankingMessageRepository;
import ee.tuleva.onboarding.ledger.FundBankLedger;
import ee.tuleva.onboarding.ledger.LedgerTransaction;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.ledger.SystemAccount;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

@SebIntegrationTest
class TradeSettlementBankFactsIntegrationTest {

  private static final Instant AFTER_EVERY_BOOKING = Instant.parse("2100-01-01T00:00:00Z");
  private static final String DEVELOPED_WORLD_ISIN = "IE00BFG1TM61";

  @Autowired private BankingMessageRepository bankingMessageRepository;
  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private BankAccounts bankAccounts;
  @Autowired private EntityManager entityManager;
  @Autowired private FundBankLedger fundBankLedger;
  @Autowired private NavLedgerRepository navLedgerRepository;

  @Test
  void tkf100TradeEntryKeepsItsSebReferenceAndPriceOnTheLedgerTransaction() {
    persistMessage(
        statement(
            TKF100,
            "TKF100-TRAD-1",
            "209080.26",
            "DBIT",
            "TRAD",
            "DLA0553690/EJAP GY/11704/17.864/Buy/ Euroclear, ABNCNL2AXXX, 14448"));

    eventPublisher.publishEvent(new ProcessBankMessagesRequested());

    assertThat(tradeSettlementMetadata(TKF100, "TKF100-TRAD-1"))
        .containsEntry("brokerReference", "DLA0553690")
        .hasEntrySatisfying(
            "price",
            price ->
                assertThat(new BigDecimal(price.toString()))
                    .isEqualByComparingTo(new BigDecimal("17.864")));
  }

  @Test
  void pensionFundTradeEntryKeepsItsSebReferenceAndPriceOnTheLedgerTransaction() {
    persistMessage(
        statement(
            TUK75,
            "TUK75-TRAD-1",
            "995377.50",
            "DBIT",
            "TRAD",
            "DLA0553685/XRSM GY/19422/51.25/Buy/ Euroclear, ABNCNL2AXXX, 14448"));

    eventPublisher.publishEvent(new ProcessBankMessagesRequested());

    assertThat(tradeSettlementMetadata(TUK75, "TUK75-TRAD-1"))
        .containsEntry("brokerReference", "DLA0553685")
        .hasEntrySatisfying(
            "price",
            price ->
                assertThat(new BigDecimal(price.toString()))
                    .isEqualByComparingTo(new BigDecimal("51.25")));
  }

  @Test
  void tkf100FundRedemptionSettlesAsATradeWithItsUnitsLeavingCustody() {
    persistMessage(
        statement(
            TKF100,
            "TKF100-REDM-1",
            "1969232.78",
            "CRDT",
            "REDM",
            "DLA0695877/BDWTEIA/59145/33.295/Sale/ BlackRock Asset Management Ireland Ltd"));

    eventPublisher.publishEvent(new ProcessBankMessagesRequested());

    assertThat(fundBankLedger.countUnresolvedUnclassifiedEntriesBefore(TKF100, AFTER_EVERY_BOOKING))
        .isZero();
    assertThat(instrumentBalance(SECURITIES_CUSTODY, TKF100, DEVELOPED_WORLD_ISIN))
        .isEqualByComparingTo(new BigDecimal("-59145"));
    assertThat(instrumentBalance(TRADE_CASH_SETTLEMENT, TKF100, DEVELOPED_WORLD_ISIN))
        .isEqualByComparingTo(new BigDecimal("-1969232.78"));
    assertThat(tradeSettlementMetadata(TKF100, "TKF100-REDM-1"))
        .containsEntry("brokerReference", "DLA0695877");
  }

  private BigDecimal instrumentBalance(SystemAccount systemAccount, TulevaFund fund, String isin) {
    return navLedgerRepository.getSystemAccountBalance(systemAccount.getAccountName(fund, isin));
  }

  private Map<String, Object> tradeSettlementMetadata(TulevaFund fund, String entryReference) {
    entityManager.flush();
    entityManager.clear();
    return entityManager
        .createQuery(
            "select t from LedgerTransaction t where t.externalReference = :externalReference"
                + " and t.transactionType = :transactionType",
            LedgerTransaction.class)
        .setParameter("externalReference", externalReference(fund, entryReference))
        .setParameter("transactionType", TRADE_SETTLEMENT)
        .getSingleResult()
        .getMetadata();
  }

  private UUID externalReference(TulevaFund fund, String entryReference) {
    var iban = bankAccounts.getIban(fund, FUND_INVESTMENT_EUR);
    return UUID.nameUUIDFromBytes((iban + ":" + entryReference).getBytes(UTF_8));
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

  private String statement(
      TulevaFund fund,
      String entryReference,
      String amount,
      String creditDebit,
      String subFamilyCode,
      String remittanceInformation) {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.053.001.02">
          <BkToCstmrStmt>
            <GrpHdr>
              <MsgId>test-trade-%2$s</MsgId>
              <CreDtTm>2026-02-11T01:00:00</CreDtTm>
            </GrpHdr>
            <Stmt>
              <Id>test-stmt-trade-%2$s</Id>
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
                  <Dt>2026-02-10</Dt>
                </Dt>
              </Bal>
              <Bal>
                <Tp>
                  <CdOrPrtry>
                    <Cd>CLBD</Cd>
                  </CdOrPrtry>
                </Tp>
                <Amt Ccy="EUR">%3$s</Amt>
                <CdtDbtInd>%4$s</CdtDbtInd>
                <Dt>
                  <Dt>2026-02-10</Dt>
                </Dt>
              </Bal>
              <Ntry>
                <NtryRef>%2$s</NtryRef>
                <Amt Ccy="EUR">%3$s</Amt>
                <CdtDbtInd>%4$s</CdtDbtInd>
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
                      <SubFmlyCd>%5$s</SubFmlyCd>
                    </Fmly>
                  </Domn>
                </BkTxCd>
                <NtryDtls>
                  <TxDtls>
                    <Refs>
                      <AcctSvcrRef>%2$s</AcctSvcrRef>
                    </Refs>
                    <RmtInf>
                      <Ustrd>%6$s</Ustrd>
                    </RmtInf>
                  </TxDtls>
                </NtryDtls>
              </Ntry>
            </Stmt>
          </BkToCstmrStmt>
        </Document>
        """
        .formatted(
            bankAccounts.getIban(fund, FUND_INVESTMENT_EUR),
            entryReference,
            amount,
            creditDebit,
            subFamilyCode,
            remittanceInformation);
  }
}
