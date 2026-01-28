package ee.tuleva.onboarding.swedbank.statement;

import static ee.tuleva.onboarding.swedbank.Swedbank.SWEDBANK_GATEWAY_TIME_ZONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.banking.statement.BankStatement.BankStatementType;
import ee.tuleva.onboarding.banking.statement.BankStatementExtractor;
import ee.tuleva.onboarding.banking.statement.BankStatementParseException;
import ee.tuleva.onboarding.banking.xml.Iso20022Marshaller;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SwedbankBankStatementExtractorIntraDayTest {

  private BankStatementExtractor extractor;

  @BeforeEach
  void setUp() {
    Iso20022Marshaller marshaller = new Iso20022Marshaller();
    extractor = new BankStatementExtractor(marshaller);
  }

  @Test
  void extractFromIntraDayReport_shouldExtractBankStatement() {
    // given
    String rawXml = createCamt052Xml();

    // when
    var statement = extractor.extractFromIntraDayReport(rawXml, SWEDBANK_GATEWAY_TIME_ZONE);

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
    assertThatThrownBy(() -> extractor.extractFromIntraDayReport(null, SWEDBANK_GATEWAY_TIME_ZONE))
        .isInstanceOf(BankStatementParseException.class)
        .hasMessage("Raw XML is null or empty");
  }

  @Test
  void extractFromIntraDayReport_shouldThrowExceptionForEmptyXml() {
    assertThatThrownBy(() -> extractor.extractFromIntraDayReport("", SWEDBANK_GATEWAY_TIME_ZONE))
        .isInstanceOf(BankStatementParseException.class)
        .hasMessage("Raw XML is null or empty");
  }

  @Test
  void extractFromIntraDayReport_shouldThrowExceptionForBlankXml() {
    assertThatThrownBy(() -> extractor.extractFromIntraDayReport("   ", SWEDBANK_GATEWAY_TIME_ZONE))
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

    assertThatThrownBy(
            () -> extractor.extractFromIntraDayReport(emptyReportXml, SWEDBANK_GATEWAY_TIME_ZONE))
        .isInstanceOf(BankStatementParseException.class)
        .hasMessageContaining("Expected exactly one report");
  }

  @org.junit.jupiter.api.Nested
  class BankAccountIssues {

    @Test
    void extractFromIntraDayReport_shouldThrowExceptionWhenAccountHolderNameIsNull() {
      String xmlWithoutAccountHolderName =
          """
          <?xml version="1.0" encoding="UTF-8"?>
          <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.052.001.02">
            <BkToCstmrAcctRpt>
              <GrpHdr>
                <MsgId>test</MsgId>
                <CreDtTm>2025-09-29T15:37:46</CreDtTm>
              </GrpHdr>
              <Rpt>
                <Id>test</Id>
                <CreDtTm>2025-09-29T15:37:46</CreDtTm>
                <Acct>
                  <Id>
                    <IBAN>EE442200221092874625</IBAN>
                  </Id>
                  <Ccy>EUR</Ccy>
                  <Ownr>
                    <!-- Missing Nm element -->
                    <Id>
                      <OrgId>
                        <Othr>
                          <Id>14118923</Id>
                        </Othr>
                      </OrgId>
                    </Id>
                  </Ownr>
                </Acct>
              </Rpt>
            </BkToCstmrAcctRpt>
          </Document>
          """;

      assertThatThrownBy(
              () ->
                  extractor.extractFromIntraDayReport(
                      xmlWithoutAccountHolderName, SWEDBANK_GATEWAY_TIME_ZONE))
          .isInstanceOf(BankStatementParseException.class)
          .hasMessageContaining("account holder name is required");
    }

    @Test
    void extractFromIntraDayReport_shouldThrowExceptionWhenAccountHolderHasNoIdCodes() {
      String xmlWithoutIdCodes =
          """
          <?xml version="1.0" encoding="UTF-8"?>
          <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.052.001.02">
            <BkToCstmrAcctRpt>
              <GrpHdr>
                <MsgId>test</MsgId>
                <CreDtTm>2025-09-29T15:37:46</CreDtTm>
              </GrpHdr>
              <Rpt>
                <Id>test</Id>
                <CreDtTm>2025-09-29T15:37:46</CreDtTm>
                <Acct>
                  <Id>
                    <IBAN>EE442200221092874625</IBAN>
                  </Id>
                  <Ccy>EUR</Ccy>
                  <Ownr>
                    <Nm>TULEVA FONDID AS</Nm>
                    <!-- Missing Id element -->
                  </Ownr>
                </Acct>
              </Rpt>
            </BkToCstmrAcctRpt>
          </Document>
          """;

      assertThatThrownBy(
              () ->
                  extractor.extractFromIntraDayReport(
                      xmlWithoutIdCodes, SWEDBANK_GATEWAY_TIME_ZONE))
          .isInstanceOf(BankStatementParseException.class)
          .hasMessageContaining("Expected exactly one account holder ID code, but found: 0");
    }

    @Test
    void extractFromIntraDayReport_shouldThrowExceptionWhenAccountHolderHasMultipleIdCodes() {
      String xmlWithMultipleIdCodes =
          """
          <?xml version="1.0" encoding="UTF-8"?>
          <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.052.001.02">
            <BkToCstmrAcctRpt>
              <GrpHdr>
                <MsgId>test</MsgId>
                <CreDtTm>2025-09-29T15:37:46</CreDtTm>
              </GrpHdr>
              <Rpt>
                <Id>test</Id>
                <CreDtTm>2025-09-29T15:37:46</CreDtTm>
                <Acct>
                  <Id>
                    <IBAN>EE442200221092874625</IBAN>
                  </Id>
                  <Ccy>EUR</Ccy>
                  <Ownr>
                    <Nm>TULEVA FONDID AS</Nm>
                    <Id>
                      <OrgId>
                        <Othr>
                          <Id>14118923</Id>
                        </Othr>
                        <Othr>
                          <Id>14118924</Id>
                        </Othr>
                      </OrgId>
                    </Id>
                  </Ownr>
                </Acct>
              </Rpt>
            </BkToCstmrAcctRpt>
          </Document>
          """;

      assertThatThrownBy(
              () ->
                  extractor.extractFromIntraDayReport(
                      xmlWithMultipleIdCodes, SWEDBANK_GATEWAY_TIME_ZONE))
          .isInstanceOf(BankStatementParseException.class)
          .hasMessageContaining("Expected exactly one account holder ID code, but found: 2");
    }

    @Test
    void extractFromIntraDayReport_shouldThrowExceptionWhenAccountIbanIsMissing() {
      String xmlWithoutAccountIban =
          """
          <?xml version="1.0" encoding="UTF-8"?>
          <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.052.001.02">
            <BkToCstmrAcctRpt>
              <GrpHdr>
                <MsgId>test</MsgId>
                <CreDtTm>2025-09-29T15:37:46</CreDtTm>
              </GrpHdr>
              <Rpt>
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
              </Rpt>
            </BkToCstmrAcctRpt>
          </Document>
          """;

      assertThatThrownBy(
              () ->
                  extractor.extractFromIntraDayReport(
                      xmlWithoutAccountIban, SWEDBANK_GATEWAY_TIME_ZONE))
          .isInstanceOf(BankStatementParseException.class)
          .hasMessageContaining("account IBAN is required");
    }
  }

  @org.junit.jupiter.api.Nested
  class EntriesIssues {

    @Test
    void extractFromIntraDayReport_shouldThrowExceptionForMissingRemittanceInformation() {
      var entries =
          List.of(
              """
          <Ntry>
            <NtryRef>test-ref</NtryRef>
            <Amt Ccy="EUR">100.00</Amt>
            <CdtDbtInd>CRDT</CdtDbtInd>
            <Sts>BOOK</Sts>
            <BookgDt>
              <Dt>2025-09-29</Dt>
            </BookgDt>
            <ValDt>
              <Dt>2025-09-29</Dt>
            </ValDt>
            <NtryDtls>
              <TxDtls>
                <Refs>
                  <AcctSvcrRef>test-ref</AcctSvcrRef>
                  <EndToEndId>test-id</EndToEndId>
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

      String xmlWithMissingRemittance = createCamt052Xml(entries);

      assertThatThrownBy(
              () ->
                  extractor.extractFromIntraDayReport(
                      xmlWithMissingRemittance, SWEDBANK_GATEWAY_TIME_ZONE))
          .isInstanceOf(BankStatementParseException.class)
          .hasMessageContaining("Expected exactly one remittance information, but found: 0");
    }

    @Test
    void extractFromIntraDayReport_shouldAllowMissingPersonalCode() {
      var entries =
          List.of(
              """
          <Ntry>
            <NtryRef>test-ref</NtryRef>
            <Amt Ccy="EUR">100.00</Amt>
            <CdtDbtInd>CRDT</CdtDbtInd>
            <Sts>BOOK</Sts>
            <BookgDt>
              <Dt>2025-09-29</Dt>
            </BookgDt>
            <ValDt>
              <Dt>2025-09-29</Dt>
            </ValDt>
            <NtryDtls>
              <TxDtls>
                <Refs>
                  <AcctSvcrRef>test-ref</AcctSvcrRef>
                  <EndToEndId>test-id</EndToEndId>
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

      String xmlWithMissingPersonalCode = createCamt052Xml(entries);

      var statement =
          extractor.extractFromIntraDayReport(
              xmlWithMissingPersonalCode, SWEDBANK_GATEWAY_TIME_ZONE);

      assertThat(statement).isNotNull();
      assertThat(statement.getEntries()).hasSize(1);
      assertThat(statement.getEntries().getFirst().details().getPersonalCode()).isEmpty();
    }

    @Test
    void extractFromIntraDayReport_shouldThrowExceptionForMultiplePersonalCodes() {
      var entries =
          List.of(
              """
          <Ntry>
            <NtryRef>test-ref</NtryRef>
            <Amt Ccy="EUR">100.00</Amt>
            <CdtDbtInd>CRDT</CdtDbtInd>
            <Sts>BOOK</Sts>
            <BookgDt>
              <Dt>2025-09-29</Dt>
            </BookgDt>
            <ValDt>
              <Dt>2025-09-29</Dt>
            </ValDt>
            <NtryDtls>
              <TxDtls>
                <Refs>
                  <AcctSvcrRef>test-ref</AcctSvcrRef>
                  <EndToEndId>test-id</EndToEndId>
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

      String xmlWithMultiplePersonalCodes = createCamt052Xml(entries);

      assertThatThrownBy(
              () ->
                  extractor.extractFromIntraDayReport(
                      xmlWithMultiplePersonalCodes, SWEDBANK_GATEWAY_TIME_ZONE))
          .isInstanceOf(BankStatementParseException.class)
          .hasMessageContaining("Expected at most one personal ID code, but found: 2");
    }

    @Test
    void extractFromIntraDayReport_shouldReturnNullDetailsForMissingCounterPartyIban() {
      var entries =
          List.of(
              """
          <Ntry>
            <NtryRef>test-ref</NtryRef>
            <Amt Ccy="EUR">100.00</Amt>
            <CdtDbtInd>CRDT</CdtDbtInd>
            <Sts>BOOK</Sts>
            <BookgDt>
              <Dt>2025-09-29</Dt>
            </BookgDt>
            <ValDt>
              <Dt>2025-09-29</Dt>
            </ValDt>
            <NtryDtls>
              <TxDtls>
                <Refs>
                  <AcctSvcrRef>test-ref</AcctSvcrRef>
                  <EndToEndId>test-id</EndToEndId>
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

      String xmlWithMissingCounterPartyIban = createCamt052Xml(entries);

      var statement =
          extractor.extractFromIntraDayReport(
              xmlWithMissingCounterPartyIban, SWEDBANK_GATEWAY_TIME_ZONE);

      assertThat(statement.getEntries()).hasSize(1);
      assertThat(statement.getEntries().getFirst().details()).isNull();
    }

    @Test
    void extractFromIntraDayReport_shouldHandleBankOperationWithoutCounterparty() {
      var entries =
          List.of(
              """
          <Ntry>
            <NtryRef>fee-ref-123</NtryRef>
            <Amt Ccy="EUR">1.50</Amt>
            <CdtDbtInd>DBIT</CdtDbtInd>
            <Sts>BOOK</Sts>
            <BookgDt>
              <Dt>2025-09-29</Dt>
            </BookgDt>
            <ValDt>
              <Dt>2025-09-29</Dt>
            </ValDt>
            <BkTxCd>
              <Domn>
                <Cd>ACMT</Cd>
                <Fmly>
                  <Cd>MCOP</Cd>
                  <SubFmlyCd>FEES</SubFmlyCd>
                </Fmly>
              </Domn>
            </BkTxCd>
            <NtryDtls>
              <TxDtls>
                <Refs>
                  <AcctSvcrRef>fee-ref-123</AcctSvcrRef>
                </Refs>
                <RmtInf>
                  <Ustrd>Konto kuutasu</Ustrd>
                </RmtInf>
              </TxDtls>
            </NtryDtls>
          </Ntry>
          """
                  .stripIndent());

      String xmlWithBankOperation = createCamt052Xml(entries);

      var statement =
          extractor.extractFromIntraDayReport(xmlWithBankOperation, SWEDBANK_GATEWAY_TIME_ZONE);

      assertThat(statement.getEntries()).hasSize(1);
      var entry = statement.getEntries().getFirst();
      assertThat(entry.details()).isNull();
      assertThat(entry.subFamilyCode()).isEqualTo("FEES");
      assertThat(entry.amount()).isEqualByComparingTo("-1.50");
      assertThat(entry.remittanceInformation()).isEqualTo("Konto kuutasu");
    }

    @Test
    void extractFromIntraDayReport_shouldHandleInterestPaymentWithoutCounterparty() {
      var entries =
          List.of(
              """
          <Ntry>
            <NtryRef>interest-ref-456</NtryRef>
            <Amt Ccy="EUR">5.25</Amt>
            <CdtDbtInd>CRDT</CdtDbtInd>
            <Sts>BOOK</Sts>
            <BookgDt>
              <Dt>2025-09-29</Dt>
            </BookgDt>
            <ValDt>
              <Dt>2025-09-29</Dt>
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
                  <AcctSvcrRef>interest-ref-456</AcctSvcrRef>
                </Refs>
                <RmtInf>
                  <Ustrd>Intresside väljamakse EUR</Ustrd>
                </RmtInf>
              </TxDtls>
            </NtryDtls>
          </Ntry>
          """
                  .stripIndent());

      String xmlWithInterest = createCamt052Xml(entries);

      var statement =
          extractor.extractFromIntraDayReport(xmlWithInterest, SWEDBANK_GATEWAY_TIME_ZONE);

      assertThat(statement.getEntries()).hasSize(1);
      var entry = statement.getEntries().getFirst();
      assertThat(entry.details()).isNull();
      assertThat(entry.subFamilyCode()).isEqualTo("INTR");
      assertThat(entry.amount()).isEqualByComparingTo("5.25");
      assertThat(entry.remittanceInformation()).isEqualTo("Intresside väljamakse EUR");
    }

    @Test
    void extractFromIntraDayReport_shouldThrowExceptionForMultipleEntryDetails() {
      var entries =
          List.of(
              """
          <Ntry>
            <NtryRef>test-ref</NtryRef>
            <Amt Ccy="EUR">100.00</Amt>
            <CdtDbtInd>CRDT</CdtDbtInd>
            <Sts>BOOK</Sts>
            <BookgDt>
              <Dt>2025-09-29</Dt>
            </BookgDt>
            <ValDt>
              <Dt>2025-09-29</Dt>
            </ValDt>
            <NtryDtls>
              <TxDtls>
                <Refs>
                  <AcctSvcrRef>test-ref</AcctSvcrRef>
                </Refs>
                <RmtInf>
                  <Ustrd>Test payment</Ustrd>
                </RmtInf>
              </TxDtls>
            </NtryDtls>
            <NtryDtls>
              <TxDtls>
                <Refs>
                  <AcctSvcrRef>test-ref-2</AcctSvcrRef>
                </Refs>
                <RmtInf>
                  <Ustrd>Another payment</Ustrd>
                </RmtInf>
              </TxDtls>
            </NtryDtls>
          </Ntry>
          """
                  .stripIndent());

      String xmlWithMultipleEntryDetails = createCamt052Xml(entries);

      assertThatThrownBy(
              () ->
                  extractor.extractFromIntraDayReport(
                      xmlWithMultipleEntryDetails, SWEDBANK_GATEWAY_TIME_ZONE))
          .isInstanceOf(BankStatementParseException.class)
          .hasMessageContaining("Expected exactly one entry details, but found: 2");
    }

    @Test
    void extractFromIntraDayReport_shouldThrowExceptionForMultipleTransactionDetails() {
      var entries =
          List.of(
              """
          <Ntry>
            <NtryRef>test-ref</NtryRef>
            <Amt Ccy="EUR">100.00</Amt>
            <CdtDbtInd>CRDT</CdtDbtInd>
            <Sts>BOOK</Sts>
            <BookgDt>
              <Dt>2025-09-29</Dt>
            </BookgDt>
            <ValDt>
              <Dt>2025-09-29</Dt>
            </ValDt>
            <NtryDtls>
              <TxDtls>
                <Refs>
                  <AcctSvcrRef>test-ref-1</AcctSvcrRef>
                </Refs>
                <AmtDtls>
                  <InstdAmt>
                    <Amt Ccy="EUR">50.00</Amt>
                  </InstdAmt>
                </AmtDtls>
                <RltdPties>
                  <Dbtr>
                    <Nm>First Person</Nm>
                  </Dbtr>
                  <DbtrAcct>
                    <Id>
                      <IBAN>EE123456789012345678</IBAN>
                    </Id>
                  </DbtrAcct>
                </RltdPties>
                <RmtInf>
                  <Ustrd>First payment</Ustrd>
                </RmtInf>
              </TxDtls>
              <TxDtls>
                <Refs>
                  <AcctSvcrRef>test-ref-2</AcctSvcrRef>
                </Refs>
                <AmtDtls>
                  <InstdAmt>
                    <Amt Ccy="EUR">50.00</Amt>
                  </InstdAmt>
                </AmtDtls>
                <RltdPties>
                  <Dbtr>
                    <Nm>Second Person</Nm>
                  </Dbtr>
                  <DbtrAcct>
                    <Id>
                      <IBAN>EE987654321098765432</IBAN>
                    </Id>
                  </DbtrAcct>
                </RltdPties>
                <RmtInf>
                  <Ustrd>Second payment</Ustrd>
                </RmtInf>
              </TxDtls>
            </NtryDtls>
          </Ntry>
          """
                  .stripIndent());

      String xmlWithMultipleTransactionDetails = createCamt052Xml(entries);

      assertThatThrownBy(
              () ->
                  extractor.extractFromIntraDayReport(
                      xmlWithMultipleTransactionDetails, SWEDBANK_GATEWAY_TIME_ZONE))
          .isInstanceOf(BankStatementParseException.class)
          .hasMessageContaining("Expected exactly one transaction details, but found: 2");
    }

    @Test
    void extractFromIntraDayReport_shouldHandleEmptyEntries() {
      var entries = List.<String>of();
      String xmlWithEmptyEntries = createCamt052Xml(entries);

      var statement =
          extractor.extractFromIntraDayReport(xmlWithEmptyEntries, SWEDBANK_GATEWAY_TIME_ZONE);

      assertThat(statement).isNotNull();
      assertThat(statement.getEntries()).isEmpty();
    }
  }

  private String createCamt052Xml(List<String> entries) {
    StringBuilder entriesXml = new StringBuilder();
    for (String entry : entries) {
      entriesXml.append("      ").append(entry).append("\n");
    }

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
        """
        + entriesXml
        + """
            </Rpt>
          </BkToCstmrAcctRpt>
        </Document>
        """
            .stripIndent();
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
