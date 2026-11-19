package ee.tuleva.onboarding.banking.processor;

import static ee.tuleva.onboarding.banking.BankType.SEB;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.banking.message.BankingMessage;
import ee.tuleva.onboarding.banking.message.BankingMessageRepository;
import ee.tuleva.onboarding.banking.processor.TradeSettlementParser.TradeSettlementInfo;
import ee.tuleva.onboarding.banking.statement.BankStatementExtractor;
import ee.tuleva.onboarding.banking.xml.Iso20022Marshaller;
import ee.tuleva.onboarding.ledger.TradeSettlementDetails;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StoredStatementTradeReferencesTest {

  private static final String FUND_IBAN = "EE001234567890123456";
  private static final String REMITTANCE =
      "DLA0933862/XWSC/16031199/9.999/Buy/ DWS Investment S.A.";

  @Mock private BankingMessageRepository messageRepository;
  @Mock private TradeSettlementParser tradeSettlementParser;

  @Test
  void aStatementStoredBeforeItsTypeWasRecordedStillGivesItsTradeReference() {
    UUID messageId = UUID.randomUUID();
    given(messageRepository.findIdsOfProcessedMessages()).willReturn(List.of(messageId));
    given(messageRepository.findById(messageId))
        .willReturn(Optional.of(untypedMessage(messageId, historicStatementWithATrade())));
    givenTheRemittanceNamesTheTrade();

    var references = storedStatementTradeReferences().byLedgerExternalReference();

    assertThat(references)
        .containsExactlyEntriesOf(
            Map.of(UUID.nameUUIDFromBytes((FUND_IBAN + ":TRADE-1").getBytes(UTF_8)), "DLA0933862"));
  }

  @Test
  void anIntraDayReportStoredBeforeItsTypeWasRecordedStillGivesItsTradeReference() {
    UUID messageId = UUID.randomUUID();
    given(messageRepository.findIdsOfProcessedMessages()).willReturn(List.of(messageId));
    given(messageRepository.findById(messageId))
        .willReturn(Optional.of(untypedMessage(messageId, intraDayReportWithATrade())));
    givenTheRemittanceNamesTheTrade();

    var references = storedStatementTradeReferences().byLedgerExternalReference();

    assertThat(references)
        .containsExactlyEntriesOf(
            Map.of(UUID.nameUUIDFromBytes((FUND_IBAN + ":TRADE-1").getBytes(UTF_8)), "DLA0933862"));
  }

  private void givenTheRemittanceNamesTheTrade() {
    given(tradeSettlementParser.parse(REMITTANCE))
        .willReturn(
            Optional.of(
                new TradeSettlementInfo(
                    new TradeSettlementDetails(
                        "DLA0933862", "IE000I9HGDZ3", "XWSC", "Xtrackers", new BigDecimal("9.999")),
                    new BigDecimal("16031199"))));
  }

  private StoredStatementTradeReferences storedStatementTradeReferences() {
    return new StoredStatementTradeReferences(
        messageRepository,
        new BankStatementExtractor(new Iso20022Marshaller()),
        tradeSettlementParser);
  }

  private static BankingMessage untypedMessage(UUID id, String xml) {
    return BankingMessage.builder()
        .id(id)
        .bankType(SEB)
        .requestId(id.toString())
        .trackingId(id.toString())
        .rawResponse(xml)
        .timezone("Europe/Tallinn")
        .build();
  }

  private static String intraDayReportWithATrade() {
    return historicStatementWithATrade()
        .replace("camt.053.001.02", "camt.052.001.02")
        .replace("BkToCstmrStmt>", "BkToCstmrAcctRpt>")
        .replace("Stmt>", "Rpt>");
  }

  private static String historicStatementWithATrade() {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.053.001.02">
          <BkToCstmrStmt>
            <GrpHdr>
              <MsgId>stored-before-the-type-column</MsgId>
              <CreDtTm>2026-06-25T01:00:00</CreDtTm>
            </GrpHdr>
            <Stmt>
              <Id>stmt-1</Id>
              <CreDtTm>2026-06-25T01:00:00</CreDtTm>
              <FrToDt>
                <FrDtTm>2026-06-24T00:00:00</FrDtTm>
                <ToDtTm>2026-06-24T23:59:59</ToDtTm>
              </FrToDt>
              <Acct>
                <Id>
                  <IBAN>%s</IBAN>
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
                  <Dt>2026-06-24</Dt>
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
                  <Dt>2026-06-24</Dt>
                </Dt>
              </Bal>
              <Ntry>
                <NtryRef>TRADE-1</NtryRef>
                <Amt Ccy="EUR">160295958.80</Amt>
                <CdtDbtInd>DBIT</CdtDbtInd>
                <Sts>BOOK</Sts>
                <BookgDt>
                  <Dt>2026-06-24</Dt>
                </BookgDt>
                <ValDt>
                  <Dt>2026-06-24</Dt>
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
                      <AcctSvcrRef>TRADE-1</AcctSvcrRef>
                    </Refs>
                    <RmtInf>
                      <Ustrd>%s</Ustrd>
                    </RmtInf>
                  </TxDtls>
                </NtryDtls>
              </Ntry>
            </Stmt>
          </BkToCstmrStmt>
        </Document>
        """
        .formatted(FUND_IBAN, REMITTANCE);
  }
}
