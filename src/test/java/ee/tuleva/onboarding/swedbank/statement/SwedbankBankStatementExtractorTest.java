package ee.tuleva.onboarding.swedbank.statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.swedbank.http.SwedbankGatewayMarshaller;
import ee.tuleva.onboarding.swedbank.statement.BankStatement.BankStatementType;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SwedbankBankStatementExtractorTest {

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
    assertThat(entry.getAmount()).isEqualByComparingTo(new BigDecimal("0.10"));
    assertThat(entry.getDetails().getName()).isEqualTo("Jüri Tamm");
    assertThat(entry.getDetails().getIban()).isEqualTo("EE157700771001802057");
    assertThat(entry.getDetails().getPersonalCode()).hasValue("39910273027");
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
