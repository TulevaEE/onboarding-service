package ee.tuleva.onboarding.swedbank.statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.swedbank.http.SwedbankGatewayMarshaller;
import ee.tuleva.onboarding.swedbank.statement.BankStatement.BankStatementType;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SwedbankBankStatementExtractorIntraDayTest {

  private SwedbankBankStatementExtractor extractor;

  @BeforeEach
  void setUp() {
    SwedbankGatewayMarshaller marshaller = new SwedbankGatewayMarshaller();
    extractor = new SwedbankBankStatementExtractor(marshaller);
  }

  @Test
  void extractFromIntraDayReport_shouldExtractBankStatement() {
    // given
    String rawXml = createCamt052Xml();

    // when
    var statement = extractor.extractFromIntraDayReport(rawXml);

    // then
    assertThat(statement).isNotNull();
    assertThat(statement.getType()).isEqualTo(BankStatementType.INTRA_DAY_REPORT);
    assertThat(statement.getBankStatementAccount().iban()).isEqualTo("EE442200221092874625");
    assertThat(statement.getBankStatementAccount().accountHolderName())
        .isEqualTo("TULEVA FONDID AS");
    assertThat(statement.getBankStatementAccount().accountHolderIdCode()).isEqualTo("14118923");
    assertThat(statement.getBalances()).hasSize(4);
    assertThat(statement.getEntries()).hasSize(1);

    // Verify the entry
    var entry = statement.getEntries().getFirst();
    assertThat(entry.amount()).isEqualByComparingTo(new BigDecimal("0.10"));
    assertThat(entry.details().getName()).isEqualTo("Jüri Tamm");
    assertThat(entry.details().getIban()).isEqualTo("EE157700771001802057");
    assertThat(entry.details().getPersonalCode()).hasValue("39910273027");
  }

  @Test
  void extractFromIntraDayReport_shouldThrowExceptionForNullXml() {
    assertThatThrownBy(() -> extractor.extractFromIntraDayReport(null))
        .isInstanceOf(BankStatementParseException.class)
        .hasMessage("Raw XML is null or empty");
  }

  @Test
  void extractFromIntraDayReport_shouldThrowExceptionForEmptyXml() {
    assertThatThrownBy(() -> extractor.extractFromIntraDayReport(""))
        .isInstanceOf(BankStatementParseException.class)
        .hasMessage("Raw XML is null or empty");
  }

  @Test
  void extractFromIntraDayReport_shouldThrowExceptionForBlankXml() {
    assertThatThrownBy(() -> extractor.extractFromIntraDayReport("   "))
        .isInstanceOf(BankStatementParseException.class)
        .hasMessage("Raw XML is null or empty");
  }

  @Test
  void extractFromIntraDayReport_shouldThrowExceptionForEmptyReport() {
    String emptyReportXml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.052.001.02">
          <BkToCstmrAcctRpt>
            <GrpHdr>
              <MsgId>test</MsgId>
              <CreDtTm>2025-09-29T15:37:46</CreDtTm>
            </GrpHdr>
          </BkToCstmrAcctRpt>
        </Document>
        """;

    assertThatThrownBy(() -> extractor.extractFromIntraDayReport(emptyReportXml))
        .isInstanceOf(BankStatementParseException.class)
        .hasMessageContaining("Expected exactly one report");
  }

  private String createCamt052Xml() {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.052.001.02">
          <BkToCstmrAcctRpt>
            <GrpHdr>
              <MsgId>2025092913110088760001</MsgId>
              <CreDtTm>2025-09-29T15:37:46</CreDtTm>
            </GrpHdr>
            <Rpt>
              <Id>1311008876-EUR-1</Id>
              <CreDtTm>2025-09-29T15:37:46</CreDtTm>
              <FrToDt>
                <FrDtTm>2025-09-29T00:00:00</FrDtTm>
                <ToDtTm>2025-09-29T15:37:46</ToDtTm>
              </FrToDt>
              <Acct>
                <Id>
                  <IBAN>EE442200221092874625</IBAN>
                </Id>
                <Ccy>EUR</Ccy>
                <Ownr>
                  <Nm>TULEVA FONDID AS</Nm>
                  <PstlAdr>
                    <Ctry>EE</Ctry>
                    <AdrLine>TELLISKIVI TN 60, HARJUMAA, TALLINN</AdrLine>
                  </PstlAdr>
                  <Id>
                    <OrgId>
                      <Othr>
                        <Id>14118923</Id>
                      </Othr>
                    </OrgId>
                  </Id>
                </Ownr>
                <Svcr>
                  <FinInstnId>
                    <BIC>HABAEE2X</BIC>
                    <Nm>Swedbank AS</Nm>
                  </FinInstnId>
                </Svcr>
              </Acct>
              <Bal>
                <Tp>
                  <CdOrPrtry>
                    <Cd>OPBD</Cd>
                  </CdOrPrtry>
                </Tp>
                <CdtLine>
                  <Incl>false</Incl>
                </CdtLine>
                <Amt Ccy="EUR">0.00</Amt>
                <CdtDbtInd>CRDT</CdtDbtInd>
                <Dt>
                  <Dt>2025-09-29</Dt>
                </Dt>
              </Bal>
              <Bal>
                <Tp>
                  <CdOrPrtry>
                    <Cd>ITBD</Cd>
                  </CdOrPrtry>
                </Tp>
                <Amt Ccy="EUR">0.00</Amt>
                <CdtDbtInd>CRDT</CdtDbtInd>
                <Dt>
                  <Dt>2025-09-29</Dt>
                </Dt>
              </Bal>
              <Bal>
                <Tp>
                  <CdOrPrtry>
                    <Prtry>RESERVED</Prtry>
                  </CdOrPrtry>
                </Tp>
                <Amt Ccy="EUR">0.00</Amt>
                <CdtDbtInd>CRDT</CdtDbtInd>
                <Dt>
                  <Dt>2025-09-29</Dt>
                </Dt>
              </Bal>
              <Bal>
                <Tp>
                  <CdOrPrtry>
                    <Cd>ITAV</Cd>
                  </CdOrPrtry>
                </Tp>
                <CdtLine>
                  <Incl>true</Incl>
                  <Amt Ccy="EUR">0.00</Amt>
                </CdtLine>
                <Amt Ccy="EUR">0.00</Amt>
                <CdtDbtInd>CRDT</CdtDbtInd>
                <Dt>
                  <Dt>2025-09-29</Dt>
                </Dt>
              </Bal>
              <TxsSummry>
                <TtlCdtNtries>
                  <NbOfNtries>1</NbOfNtries>
                  <Sum>0.10</Sum>
                </TtlCdtNtries>
                <TtlDbtNtries>
                  <NbOfNtries>0</NbOfNtries>
                  <Sum>0</Sum>
                </TtlDbtNtries>
              </TxsSummry>
              <Ntry>
                <NtryRef>2025092900654847-1</NtryRef>
                <Amt Ccy="EUR">0.10</Amt>
                <CdtDbtInd>CRDT</CdtDbtInd>
                <Sts>BOOK</Sts>
                <BookgDt>
                  <Dt>2025-09-29</Dt>
                </BookgDt>
                <ValDt>
                  <Dt>2025-09-29</Dt>
                </ValDt>
                <AcctSvcrRef>2025092900654847-1</AcctSvcrRef>
                <BkTxCd>
                  <Domn>
                    <Cd>PMNT</Cd>
                    <Fmly>
                      <Cd>RCDT</Cd>
                      <SubFmlyCd>ESCT</SubFmlyCd>
                    </Fmly>
                  </Domn>
                  <Prtry>
                    <Cd>MK</Cd>
                    <Issr>Swedbank AS</Issr>
                  </Prtry>
                </BkTxCd>
                <NtryDtls>
                  <TxDtls>
                    <Refs>
                      <AcctSvcrRef>2025092900654847-1</AcctSvcrRef>
                      <EndToEndId>1277</EndToEndId>
                    </Refs>
                    <AmtDtls>
                      <InstdAmt>
                        <Amt Ccy="EUR">0.10</Amt>
                      </InstdAmt>
                      <TxAmt>
                        <Amt Ccy="EUR">0.10</Amt>
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
                    <RltdAgts>
                      <DbtrAgt>
                        <FinInstnId>
                          <BIC>LHVBEE22</BIC>
                        </FinInstnId>
                      </DbtrAgt>
                    </RltdAgts>
                    <RmtInf>
                      <Ustrd>39910273027</Ustrd>
                    </RmtInf>
                  </TxDtls>
                </NtryDtls>
              </Ntry>
            </Rpt>
          </BkToCstmrAcctRpt>
        </Document>
        """
        .stripIndent();
  }
}
