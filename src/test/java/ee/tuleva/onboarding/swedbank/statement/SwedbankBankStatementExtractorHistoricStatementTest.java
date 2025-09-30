package ee.tuleva.onboarding.swedbank.statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.swedbank.http.SwedbankGatewayMarshaller;
import ee.tuleva.onboarding.swedbank.statement.BankStatement.BankStatementType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SwedbankBankStatementExtractorHistoricStatementTest {

  private SwedbankBankStatementExtractor extractor;

  @BeforeEach
  void setUp() {
    SwedbankGatewayMarshaller marshaller = new SwedbankGatewayMarshaller();
    extractor = new SwedbankBankStatementExtractor(marshaller);
  }

  @Test
  void extractFromHistoricStatement_shouldExtractBankStatement() {
    // given
    String rawXml = createCamt053Xml();

    // when
    var statement = extractor.extractFromHistoricStatement(rawXml);

    // then
    assertThat(statement).isNotNull();
    assertThat(statement.getType()).isEqualTo(BankStatementType.HISTORIC_STATEMENT);
    assertThat(statement.getBankStatementAccount().iban()).isEqualTo("EE062200221055091966");
    assertThat(statement.getBalances()).hasSize(2);
    assertThat(statement.getEntries()).hasSize(4);

    // Verify first entry
    var firstEntry = statement.getEntries().getFirst();
    assertThat(firstEntry.getAmount()).isEqualByComparingTo(new BigDecimal("772.88"));
    assertThat(firstEntry.getDetails().getName()).isEqualTo("Toomas Raudsepp");
  }

  @Test
  void extractFromHistoricStatement_shouldThrowExceptionForEmptyStatement() {
    String emptyStatementXml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.053.001.02">
          <BkToCstmrStmt>
            <GrpHdr>
              <MsgId>test</MsgId>
              <CreDtTm>2025-09-29T15:37:46</CreDtTm>
            </GrpHdr>
          </BkToCstmrStmt>
        </Document>
        """;

    assertThatThrownBy(() -> extractor.extractFromHistoricStatement(emptyStatementXml))
        .isInstanceOf(BankStatementParseException.class)
        .hasMessageContaining("Expected exactly one statement");
  }

  @org.junit.jupiter.api.Nested
  class BankAccountIssues {

    @Test
    void extractFromHistoricStatement_shouldThrowExceptionWhenAccountHolderNameIsNull() {
      String xmlWithoutAccountHolderName =
          """
          <?xml version="1.0" encoding="UTF-8"?>
          <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.053.001.02">
            <BkToCstmrStmt>
              <GrpHdr>
                <MsgId>test</MsgId>
                <CreDtTm>2025-09-29T15:37:46</CreDtTm>
              </GrpHdr>
              <Stmt>
                <Id>test</Id>
                <CreDtTm>2025-09-29T15:37:46</CreDtTm>
                <Acct>
                  <Id>
                    <IBAN>EE062200221055091966</IBAN>
                  </Id>
                  <Ccy>EUR</Ccy>
                  <Ownr>
                    <!-- Missing Nm element -->
                    <Id>
                      <OrgId>
                        <Othr>
                          <Id>10006901</Id>
                        </Othr>
                      </OrgId>
                    </Id>
                  </Ownr>
                </Acct>
              </Stmt>
            </BkToCstmrStmt>
          </Document>
          """;

      assertThatThrownBy(() -> extractor.extractFromHistoricStatement(xmlWithoutAccountHolderName))
          .isInstanceOf(BankStatementParseException.class)
          .hasMessageContaining("account holder name is required");
    }

    @Test
    void extractFromHistoricStatement_shouldThrowExceptionWhenAccountHolderHasNoIdCodes() {
      String xmlWithoutIdCodes =
          """
          <?xml version="1.0" encoding="UTF-8"?>
          <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.053.001.02">
            <BkToCstmrStmt>
              <GrpHdr>
                <MsgId>test</MsgId>
                <CreDtTm>2025-09-29T15:37:46</CreDtTm>
              </GrpHdr>
              <Stmt>
                <Id>test</Id>
                <CreDtTm>2025-09-29T15:37:46</CreDtTm>
                <Acct>
                  <Id>
                    <IBAN>EE062200221055091966</IBAN>
                  </Id>
                  <Ccy>EUR</Ccy>
                  <Ownr>
                    <Nm>Pööripäeva Päikesekell OÜ</Nm>
                    <!-- Missing Id element -->
                  </Ownr>
                </Acct>
              </Stmt>
            </BkToCstmrStmt>
          </Document>
          """;

      assertThatThrownBy(() -> extractor.extractFromHistoricStatement(xmlWithoutIdCodes))
          .isInstanceOf(BankStatementParseException.class)
          .hasMessageContaining("Expected exactly one account holder ID code, but found: 0");
    }

    @Test
    void extractFromHistoricStatement_shouldThrowExceptionWhenAccountHolderHasMultipleIdCodes() {
      String xmlWithMultipleIdCodes =
          """
          <?xml version="1.0" encoding="UTF-8"?>
          <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.053.001.02">
            <BkToCstmrStmt>
              <GrpHdr>
                <MsgId>test</MsgId>
                <CreDtTm>2025-09-29T15:37:46</CreDtTm>
              </GrpHdr>
              <Stmt>
                <Id>test</Id>
                <CreDtTm>2025-09-29T15:37:46</CreDtTm>
                <Acct>
                  <Id>
                    <IBAN>EE062200221055091966</IBAN>
                  </Id>
                  <Ccy>EUR</Ccy>
                  <Ownr>
                    <Nm>Pööripäeva Päikesekell OÜ</Nm>
                    <Id>
                      <OrgId>
                        <Othr>
                          <Id>10006901</Id>
                        </Othr>
                        <Othr>
                          <Id>10006902</Id>
                        </Othr>
                      </OrgId>
                    </Id>
                  </Ownr>
                </Acct>
              </Stmt>
            </BkToCstmrStmt>
          </Document>
          """;

      assertThatThrownBy(() -> extractor.extractFromHistoricStatement(xmlWithMultipleIdCodes))
          .isInstanceOf(BankStatementParseException.class)
          .hasMessageContaining("Expected exactly one account holder ID code, but found: 2");
    }

    @Test
    void extractFromHistoricStatement_shouldThrowExceptionWhenAccountIbanIsMissing() {
      String xmlWithoutAccountIban =
          """
          <?xml version="1.0" encoding="UTF-8"?>
          <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.053.001.02">
            <BkToCstmrStmt>
              <GrpHdr>
                <MsgId>test</MsgId>
                <CreDtTm>2025-09-29T15:37:46</CreDtTm>
              </GrpHdr>
              <Stmt>
                <Id>test</Id>
                <CreDtTm>2025-09-29T15:37:46</CreDtTm>
                <Acct>
                  <Id>
                    <!-- Using Othr instead of IBAN -->
                    <Othr>
                      <Id>123456</Id>
                    </Othr>
                  </Id>
                  <Ccy>EUR</Ccy>
                  <Ownr>
                    <Nm>Pööripäeva Päikesekell OÜ</Nm>
                    <Id>
                      <OrgId>
                        <Othr>
                          <Id>10006901</Id>
                        </Othr>
                      </OrgId>
                    </Id>
                  </Ownr>
                </Acct>
              </Stmt>
            </BkToCstmrStmt>
          </Document>
          """;

      assertThatThrownBy(() -> extractor.extractFromHistoricStatement(xmlWithoutAccountIban))
          .isInstanceOf(BankStatementParseException.class)
          .hasMessageContaining("account IBAN is required");
    }
  }

  @org.junit.jupiter.api.Nested
  class EntriesIssues {

    @Test
    void extractFromHistoricStatement_shouldThrowExceptionForMissingRemittanceInformation() {
      var entries =
          List.of(
              """
          <Ntry>
            <NtryRef>test-ref</NtryRef>
            <Amt Ccy="EUR">100.00</Amt>
            <CdtDbtInd>CRDT</CdtDbtInd>
            <Sts>BOOK</Sts>
            <BookgDt>
              <Dt>2025-06-05</Dt>
            </BookgDt>
            <ValDt>
              <Dt>2025-06-05</Dt>
            </ValDt>
            <NtryDtls>
              <TxDtls>
                <Refs>
                  <AcctSvcrRef>test-ref</AcctSvcrRef>
                  <InstrId>test-id</InstrId>
                </Refs>
                <AmtDtls>
                  <InstdAmt>
                    <Amt Ccy="EUR">100.00</Amt>
                  </InstdAmt>
                  <TxAmt>
                    <Amt Ccy="EUR">100.00</Amt>
                  </TxAmt>
                </AmtDtls>
                <RltdPties>
                  <Dbtr>
                    <Nm>Test Person</Nm>
                    <Id>
                      <PrvtId>
                        <Othr>
                          <Id>12345678901</Id>
                          <SchmeNm>
                            <Cd>NIDN</Cd>
                          </SchmeNm>
                        </Othr>
                      </PrvtId>
                    </Id>
                  </Dbtr>
                  <DbtrAcct>
                    <Id>
                      <IBAN>EE123456789012345678</IBAN>
                    </Id>
                  </DbtrAcct>
                </RltdPties>
                <!-- Missing RmtInf element -->
              </TxDtls>
            </NtryDtls>
          </Ntry>
          """
                  .stripIndent());

      String xmlWithMissingRemittance = createCamt053Xml(entries);

      assertThatThrownBy(() -> extractor.extractFromHistoricStatement(xmlWithMissingRemittance))
          .isInstanceOf(BankStatementParseException.class)
          .hasMessageContaining("Expected exactly one remittance information, but found: 0");
    }

    @Test
    void extractFromHistoricStatement_shouldAllowMissingPersonalCode() {
      var entries =
          List.of(
              """
          <Ntry>
            <NtryRef>test-ref</NtryRef>
            <Amt Ccy="EUR">100.00</Amt>
            <CdtDbtInd>CRDT</CdtDbtInd>
            <Sts>BOOK</Sts>
            <BookgDt>
              <Dt>2025-06-05</Dt>
            </BookgDt>
            <ValDt>
              <Dt>2025-06-05</Dt>
            </ValDt>
            <NtryDtls>
              <TxDtls>
                <Refs>
                  <AcctSvcrRef>test-ref</AcctSvcrRef>
                  <InstrId>test-id</InstrId>
                </Refs>
                <AmtDtls>
                  <InstdAmt>
                    <Amt Ccy="EUR">100.00</Amt>
                  </InstdAmt>
                  <TxAmt>
                    <Amt Ccy="EUR">100.00</Amt>
                  </TxAmt>
                </AmtDtls>
                <RltdPties>
                  <Dbtr>
                    <Nm>Test Person</Nm>
                    <!-- Missing Id element -->
                  </Dbtr>
                  <DbtrAcct>
                    <Id>
                      <IBAN>EE123456789012345678</IBAN>
                    </Id>
                  </DbtrAcct>
                </RltdPties>
                <RmtInf>
                  <Ustrd>Test payment</Ustrd>
                </RmtInf>
              </TxDtls>
            </NtryDtls>
          </Ntry>
          """
                  .stripIndent());

      String xmlWithMissingPersonalCode = createCamt053Xml(entries);

      var statement = extractor.extractFromHistoricStatement(xmlWithMissingPersonalCode);

      assertThat(statement).isNotNull();
      assertThat(statement.getEntries()).hasSize(1);
      assertThat(statement.getEntries().getFirst().getDetails().getPersonalCode()).isEmpty();
    }

    @Test
    void extractFromHistoricStatement_shouldThrowExceptionForMultiplePersonalCodes() {
      var entries =
          List.of(
              """
          <Ntry>
            <NtryRef>test-ref</NtryRef>
            <Amt Ccy="EUR">100.00</Amt>
            <CdtDbtInd>CRDT</CdtDbtInd>
            <Sts>BOOK</Sts>
            <BookgDt>
              <Dt>2025-06-05</Dt>
            </BookgDt>
            <ValDt>
              <Dt>2025-06-05</Dt>
            </ValDt>
            <NtryDtls>
              <TxDtls>
                <Refs>
                  <AcctSvcrRef>test-ref</AcctSvcrRef>
                  <InstrId>test-id</InstrId>
                </Refs>
                <AmtDtls>
                  <InstdAmt>
                    <Amt Ccy="EUR">100.00</Amt>
                  </InstdAmt>
                  <TxAmt>
                    <Amt Ccy="EUR">100.00</Amt>
                  </TxAmt>
                </AmtDtls>
                <RltdPties>
                  <Dbtr>
                    <Nm>Test Person</Nm>
                    <Id>
                      <PrvtId>
                        <Othr>
                          <Id>12345678901</Id>
                          <SchmeNm>
                            <Cd>NIDN</Cd>
                          </SchmeNm>
                        </Othr>
                        <Othr>
                          <Id>98765432109</Id>
                          <SchmeNm>
                            <Cd>NIDN</Cd>
                          </SchmeNm>
                        </Othr>
                      </PrvtId>
                    </Id>
                  </Dbtr>
                  <DbtrAcct>
                    <Id>
                      <IBAN>EE123456789012345678</IBAN>
                    </Id>
                  </DbtrAcct>
                </RltdPties>
                <RmtInf>
                  <Ustrd>Test payment</Ustrd>
                </RmtInf>
              </TxDtls>
            </NtryDtls>
          </Ntry>
          """
                  .stripIndent());

      String xmlWithMultiplePersonalCodes = createCamt053Xml(entries);

      assertThatThrownBy(() -> extractor.extractFromHistoricStatement(xmlWithMultiplePersonalCodes))
          .isInstanceOf(BankStatementParseException.class)
          .hasMessageContaining("Expected at most one personal ID code, but found: 2");
    }

    @Test
    void extractFromHistoricStatement_shouldThrowExceptionForMissingCounterPartyIban() {
      var entries =
          List.of(
              """
          <Ntry>
            <NtryRef>test-ref</NtryRef>
            <Amt Ccy="EUR">100.00</Amt>
            <CdtDbtInd>CRDT</CdtDbtInd>
            <Sts>BOOK</Sts>
            <BookgDt>
              <Dt>2025-06-05</Dt>
            </BookgDt>
            <ValDt>
              <Dt>2025-06-05</Dt>
            </ValDt>
            <NtryDtls>
              <TxDtls>
                <Refs>
                  <AcctSvcrRef>test-ref</AcctSvcrRef>
                  <InstrId>test-id</InstrId>
                </Refs>
                <AmtDtls>
                  <InstdAmt>
                    <Amt Ccy="EUR">100.00</Amt>
                  </InstdAmt>
                  <TxAmt>
                    <Amt Ccy="EUR">100.00</Amt>
                  </TxAmt>
                </AmtDtls>
                <RltdPties>
                  <Dbtr>
                    <Nm>Test Person</Nm>
                    <Id>
                      <PrvtId>
                        <Othr>
                          <Id>12345678901</Id>
                          <SchmeNm>
                            <Cd>NIDN</Cd>
                          </SchmeNm>
                        </Othr>
                      </PrvtId>
                    </Id>
                  </Dbtr>
                  <DbtrAcct>
                    <Id>
                      <!-- Using Othr instead of IBAN -->
                      <Othr>
                        <Id>123456</Id>
                      </Othr>
                    </Id>
                  </DbtrAcct>
                </RltdPties>
                <RmtInf>
                  <Ustrd>Test payment</Ustrd>
                </RmtInf>
              </TxDtls>
            </NtryDtls>
          </Ntry>
          """
                  .stripIndent());

      String xmlWithMissingCounterPartyIban = createCamt053Xml(entries);

      assertThatThrownBy(
              () -> extractor.extractFromHistoricStatement(xmlWithMissingCounterPartyIban))
          .isInstanceOf(BankStatementParseException.class)
          .hasMessageContaining("counter-party IBAN is required");
    }

    @Test
    void extractFromHistoricStatement_shouldHandleEmptyEntries() {
      var entries = List.<String>of();
      String xmlWithEmptyEntries = createCamt053Xml(entries);

      var statement = extractor.extractFromHistoricStatement(xmlWithEmptyEntries);

      assertThat(statement).isNotNull();
      assertThat(statement.getEntries()).isEmpty();
    }
  }

  private String createCamt053Xml(List<String> entries) {
    StringBuilder entriesXml = new StringBuilder();
    for (String entry : entries) {
      entriesXml.append("      ").append(entry).append("\n");
    }

    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.053.001.02">
          <BkToCstmrStmt>
            <GrpHdr>
              <MsgId>202506051114025350001</MsgId>
              <CreDtTm>2025-06-05T09:04:24</CreDtTm>
            </GrpHdr>
            <Stmt>
              <Id>111402535-EUR-1</Id>
              <ElctrncSeqNb>111402535</ElctrncSeqNb>
              <CreDtTm>2025-06-05T09:04:24</CreDtTm>
              <FrToDt>
                <FrDtTm>2025-06-05T00:00:00</FrDtTm>
                <ToDtTm>2025-06-05T23:59:59</ToDtTm>
              </FrToDt>
              <Acct>
                <Id>
                  <IBAN>EE062200221055091966</IBAN>
                </Id>
                <Ccy>EUR</Ccy>
                <Ownr>
                  <Nm>Pööripäeva Päikesekell OÜ</Nm>
                  <PstlAdr>
                    <Ctry>EE</Ctry>
                    <AdrLine>Universumimaagia tee 15, Astrofantaasia, 56789</AdrLine>
                  </PstlAdr>
                  <Id>
                    <OrgId>
                      <Othr>
                        <Id>10006901</Id>
                      </Othr>
                    </OrgId>
                  </Id>
                </Ownr>
                <Svcr>
                  <FinInstnId>
                    <BIC>HABAEE2X</BIC>
                    <Nm>SWEDBANK AS</Nm>
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
                <Amt Ccy="EUR">3332.17</Amt>
                <CdtDbtInd>CRDT</CdtDbtInd>
                <Dt>
                  <Dt>2025-06-05</Dt>
                </Dt>
              </Bal>
              <Bal>
                <Tp>
                  <CdOrPrtry>
                    <Cd>CLBD</Cd>
                  </CdOrPrtry>
                </Tp>
                <Amt Ccy="EUR">766.10</Amt>
                <CdtDbtInd>CRDT</CdtDbtInd>
                <Dt>
                  <Dt>2025-06-05</Dt>
                </Dt>
              </Bal>
              <TxsSummry>
                <TtlCdtNtries>
                  <NbOfNtries>4</NbOfNtries>
                  <Sum>2566.07</Sum>
                </TtlCdtNtries>
                <TtlDbtNtries>
                  <NbOfNtries>0</NbOfNtries>
                  <Sum>0</Sum>
                </TtlDbtNtries>
              </TxsSummry>
        """
        + entriesXml
        + """
            </Stmt>
          </BkToCstmrStmt>
        </Document>
        """
            .stripIndent();
  }

  private String createCamt053Xml() {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.053.001.02">
          <BkToCstmrStmt>
            <GrpHdr>
              <MsgId>202506051114025350001</MsgId>
              <CreDtTm>2025-06-05T09:04:24</CreDtTm>
            </GrpHdr>
            <Stmt>
              <Id>111402535-EUR-1</Id>
              <ElctrncSeqNb>111402535</ElctrncSeqNb>
              <CreDtTm>2025-06-05T09:04:24</CreDtTm>
              <FrToDt>
                <FrDtTm>2025-06-05T00:00:00</FrDtTm>
                <ToDtTm>2025-06-05T23:59:59</ToDtTm>
              </FrToDt>
              <Acct>
                <Id>
                  <IBAN>EE062200221055091966</IBAN>
                </Id>
                <Ccy>EUR</Ccy>
                <Ownr>
                  <Nm>Pööripäeva Päikesekell OÜ</Nm>
                  <PstlAdr>
                    <Ctry>EE</Ctry>
                    <AdrLine>Universumimaagia tee 15, Astrofantaasia, 56789</AdrLine>
                  </PstlAdr>
                  <Id>
                    <OrgId>
                      <Othr>
                        <Id>10006901</Id>
                      </Othr>
                    </OrgId>
                  </Id>
                </Ownr>
                <Svcr>
                  <FinInstnId>
                    <BIC>HABAEE2X</BIC>
                    <Nm>SWEDBANK AS</Nm>
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
                <Amt Ccy="EUR">3332.17</Amt>
                <CdtDbtInd>CRDT</CdtDbtInd>
                <Dt>
                  <Dt>2025-06-05</Dt>
                </Dt>
              </Bal>
              <Bal>
                <Tp>
                  <CdOrPrtry>
                    <Cd>CLBD</Cd>
                  </CdOrPrtry>
                </Tp>
                <Amt Ccy="EUR">766.10</Amt>
                <CdtDbtInd>CRDT</CdtDbtInd>
                <Dt>
                  <Dt>2025-06-05</Dt>
                </Dt>
              </Bal>
              <TxsSummry>
                <TtlCdtNtries>
                  <NbOfNtries>4</NbOfNtries>
                  <Sum>2566.07</Sum>
                </TtlCdtNtries>
                <TtlDbtNtries>
                  <NbOfNtries>0</NbOfNtries>
                  <Sum>0</Sum>
                </TtlDbtNtries>
              </TxsSummry>
              <Ntry>
                <NtryRef>2025060525827070-1</NtryRef>
                <Amt Ccy="EUR">772.88</Amt>
                <CdtDbtInd>CRDT</CdtDbtInd>
                <Sts>BOOK</Sts>
                <BookgDt>
                  <Dt>2025-06-05</Dt>
                </BookgDt>
                <ValDt>
                  <Dt>2025-06-05</Dt>
                </ValDt>
                <AcctSvcrRef>2025060525827070-1</AcctSvcrRef>
                <BkTxCd>
                  <Domn>
                    <Cd>PMNT</Cd>
                    <Fmly>
                      <Cd>RCDT</Cd>
                      <SubFmlyCd>BOOK</SubFmlyCd>
                    </Fmly>
                  </Domn>
                  <Prtry>
                    <Cd>MK</Cd>
                    <Issr>SWEDBANK AS</Issr>
                  </Prtry>
                </BkTxCd>
                <NtryDtls>
                  <TxDtls>
                    <Refs>
                      <AcctSvcrRef>2025060525827070-1</AcctSvcrRef>
                      <InstrId>179</InstrId>
                    </Refs>
                    <AmtDtls>
                      <InstdAmt>
                        <Amt Ccy="EUR">772.88</Amt>
                      </InstdAmt>
                      <TxAmt>
                        <Amt Ccy="EUR">772.88</Amt>
                      </TxAmt>
                    </AmtDtls>
                    <RltdPties>
                      <Dbtr>
                        <Nm>Toomas Raudsepp</Nm>
                        <Id>
                          <PrvtId>
                            <Othr>
                              <Id>38009293505</Id>
                              <SchmeNm>
                                <Cd>NIDN</Cd>
                              </SchmeNm>
                            </Othr>
                          </PrvtId>
                        </Id>
                      </Dbtr>
                      <DbtrAcct>
                        <Id>
                          <IBAN>EE042200000000100031</IBAN>
                        </Id>
                      </DbtrAcct>
                    </RltdPties>
                    <RltdAgts>
                      <DbtrAgt>
                        <FinInstnId>
                          <BIC>HABAEE2X</BIC>
                        </FinInstnId>
                      </DbtrAgt>
                    </RltdAgts>
                    <RmtInf>
                      <Ustrd>Subscription renewal</Ustrd>
                    </RmtInf>
                  </TxDtls>
                </NtryDtls>
              </Ntry>
              <Ntry>
                <NtryRef>2025060591569146-2</NtryRef>
                <Amt Ccy="EUR">612.37</Amt>
                <CdtDbtInd>CRDT</CdtDbtInd>
                <Sts>BOOK</Sts>
                <BookgDt>
                  <Dt>2025-06-05</Dt>
                </BookgDt>
                <ValDt>
                  <Dt>2025-06-05</Dt>
                </ValDt>
                <AcctSvcrRef>2025060591569146-2</AcctSvcrRef>
                <BkTxCd>
                  <Domn>
                    <Cd>PMNT</Cd>
                    <Fmly>
                      <Cd>RCDT</Cd>
                      <SubFmlyCd>BOOK</SubFmlyCd>
                    </Fmly>
                  </Domn>
                  <Prtry>
                    <Cd>MK</Cd>
                    <Issr>SWEDBANK AS</Issr>
                  </Prtry>
                </BkTxCd>
                <NtryDtls>
                  <TxDtls>
                    <Refs>
                      <AcctSvcrRef>2025060591569146-2</AcctSvcrRef>
                      <InstrId>180</InstrId>
                    </Refs>
                    <AmtDtls>
                      <InstdAmt>
                        <Amt Ccy="EUR">612.37</Amt>
                      </InstdAmt>
                      <TxAmt>
                        <Amt Ccy="EUR">612.37</Amt>
                      </TxAmt>
                    </AmtDtls>
                    <RltdPties>
                      <Dbtr>
                        <Nm>Kristjan Värk</Nm>
                        <Id>
                          <PrvtId>
                            <Othr>
                              <Id>39609307495</Id>
                              <SchmeNm>
                                <Cd>NIDN</Cd>
                              </SchmeNm>
                            </Othr>
                          </PrvtId>
                        </Id>
                      </Dbtr>
                      <DbtrAcct>
                        <Id>
                          <IBAN>EE082200000000100056</IBAN>
                        </Id>
                      </DbtrAcct>
                    </RltdPties>
                    <RltdAgts>
                      <DbtrAgt>
                        <FinInstnId>
                          <BIC>HABAEE2X</BIC>
                        </FinInstnId>
                      </DbtrAgt>
                    </RltdAgts>
                    <RmtInf>
                      <Ustrd>Airline tickets</Ustrd>
                    </RmtInf>
                  </TxDtls>
                </NtryDtls>
              </Ntry>
              <Ntry>
                <NtryRef>2025060538364998-3</NtryRef>
                <Amt Ccy="EUR">454.76</Amt>
                <CdtDbtInd>CRDT</CdtDbtInd>
                <Sts>BOOK</Sts>
                <BookgDt>
                  <Dt>2025-06-05</Dt>
                </BookgDt>
                <ValDt>
                  <Dt>2025-06-05</Dt>
                </ValDt>
                <AcctSvcrRef>2025060538364998-3</AcctSvcrRef>
                <BkTxCd>
                  <Domn>
                    <Cd>PMNT</Cd>
                    <Fmly>
                      <Cd>RCDT</Cd>
                      <SubFmlyCd>BOOK</SubFmlyCd>
                    </Fmly>
                  </Domn>
                  <Prtry>
                    <Cd>MK</Cd>
                    <Issr>SWEDBANK AS</Issr>
                  </Prtry>
                </BkTxCd>
                <NtryDtls>
                  <TxDtls>
                    <Refs>
                      <AcctSvcrRef>2025060538364998-3</AcctSvcrRef>
                      <InstrId>181</InstrId>
                    </Refs>
                    <AmtDtls>
                      <InstdAmt>
                        <Amt Ccy="EUR">454.76</Amt>
                      </InstdAmt>
                      <TxAmt>
                        <Amt Ccy="EUR">454.76</Amt>
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
                          <IBAN>EE532200000000010006</IBAN>
                        </Id>
                      </DbtrAcct>
                    </RltdPties>
                    <RltdAgts>
                      <DbtrAgt>
                        <FinInstnId>
                          <BIC>HABAEE2X</BIC>
                        </FinInstnId>
                      </DbtrAgt>
                    </RltdAgts>
                    <RmtInf>
                      <Ustrd>Charity donation</Ustrd>
                    </RmtInf>
                  </TxDtls>
                </NtryDtls>
              </Ntry>
              <Ntry>
                <NtryRef>2025060515070077-4</NtryRef>
                <Amt Ccy="EUR">726.06</Amt>
                <CdtDbtInd>CRDT</CdtDbtInd>
                <Sts>BOOK</Sts>
                <BookgDt>
                  <Dt>2025-06-05</Dt>
                </BookgDt>
                <ValDt>
                  <Dt>2025-06-05</Dt>
                </ValDt>
                <AcctSvcrRef>2025060515070077-4</AcctSvcrRef>
                <BkTxCd>
                  <Domn>
                    <Cd>PMNT</Cd>
                    <Fmly>
                      <Cd>RCDT</Cd>
                      <SubFmlyCd>BOOK</SubFmlyCd>
                    </Fmly>
                  </Domn>
                  <Prtry>
                    <Cd>MK</Cd>
                    <Issr>SWEDBANK AS</Issr>
                  </Prtry>
                </BkTxCd>
                <NtryDtls>
                  <TxDtls>
                    <Refs>
                      <AcctSvcrRef>2025060515070077-4</AcctSvcrRef>
                      <InstrId>182</InstrId>
                    </Refs>
                    <AmtDtls>
                      <InstdAmt>
                        <Amt Ccy="EUR">726.06</Amt>
                      </InstdAmt>
                      <TxAmt>
                        <Amt Ccy="EUR">726.06</Amt>
                      </TxAmt>
                    </AmtDtls>
                    <RltdPties>
                      <Dbtr>
                        <Nm>Rasmus Lind</Nm>
                        <Id>
                          <PrvtId>
                            <Othr>
                              <Id>37603135585</Id>
                              <SchmeNm>
                                <Cd>NIDN</Cd>
                              </SchmeNm>
                            </Othr>
                          </PrvtId>
                        </Id>
                      </Dbtr>
                      <DbtrAcct>
                        <Id>
                          <IBAN>EE522200000000100040</IBAN>
                        </Id>
                      </DbtrAcct>
                    </RltdPties>
                    <RltdAgts>
                      <DbtrAgt>
                        <FinInstnId>
                          <BIC>HABAEE2X</BIC>
                        </FinInstnId>
                      </DbtrAgt>
                    </RltdAgts>
                    <RmtInf>
                      <Ustrd>Books</Ustrd>
                    </RmtInf>
                  </TxDtls>
                </NtryDtls>
              </Ntry>
            </Stmt>
          </BkToCstmrStmt>
        </Document>
        """
        .stripIndent();
  }
}
