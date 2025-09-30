package ee.tuleva.onboarding.savings.fund;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.swedbank.gateway.iso.response.report.Document;
import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.swedbank.http.SwedbankGatewayMarshaller;
import ee.tuleva.onboarding.swedbank.statement.BankStatement;
import jakarta.xml.bind.JAXBElement;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled // TODO: rework tests, instead of parsing XML, create BankStatement directly
class SavingFundPaymentExtractorTest {

  private final SavingFundPaymentExtractor extractor = new SavingFundPaymentExtractor();
  private final SwedbankGatewayMarshaller marshaller = new SwedbankGatewayMarshaller();

  @Test
  void extractPayments_shouldExtractPaymentsFromDocument() {
    // given
    List<String> entries =
        List.of(
            """
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
        """
                .stripIndent(),
            """
        <Ntry>
          <NtryRef>2025092900673437-1</NtryRef>
          <Amt Ccy="EUR">0.10</Amt>
          <CdtDbtInd>DBIT</CdtDbtInd>
          <Sts>BOOK</Sts>
          <BookgDt>
            <Dt>2025-09-29</Dt>
          </BookgDt>
          <ValDt>
            <Dt>2025-09-29</Dt>
          </ValDt>
          <AcctSvcrRef>2025092900673437-1</AcctSvcrRef>
          <BkTxCd>
            <Domn>
              <Cd>PMNT</Cd>
              <Fmly>
                <Cd>ICDT</Cd>
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
                <AcctSvcrRef>2025092900673437-1</AcctSvcrRef>
                <InstrId>1</InstrId>
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
                <Cdtr>
                  <Nm>Jüri Tamm</Nm>
                </Cdtr>
                <CdtrAcct>
                  <Id>
                    <IBAN>EE157700771001802057</IBAN>
                  </Id>
                </CdtrAcct>
              </RltdPties>
              <RltdAgts>
                <CdtrAgt>
                  <FinInstnId>
                    <BIC>LHVBEE22XXX</BIC>
                  </FinInstnId>
                </CdtrAgt>
              </RltdAgts>
              <RmtInf>
                <Ustrd>bounce-back</Ustrd>
              </RmtInf>
            </TxDtls>
          </NtryDtls>
        </Ntry>
        """
                .stripIndent());

    String swedbankXmlResponse = createCamtXml(entries);
    JAXBElement<Document> response =
        marshaller.unMarshal(
            swedbankXmlResponse,
            JAXBElement.class,
            ee.swedbank.gateway.iso.response.report.ObjectFactory.class);
    Document document = response.getValue();
    Instant receivedAt = Instant.parse("2025-09-29T15:37:46Z");
    var statement = BankStatement.from(document.getBkToCstmrAcctRpt());

    // when
    List<SavingFundPayment> payments = extractor.extractPayments(statement, receivedAt);

    // then
    assertThat(payments).hasSize(2);

    // Verify first payment (credit transaction)
    SavingFundPayment firstPayment = payments.get(0);
    assertThat(firstPayment.getAmount()).isEqualByComparingTo(new BigDecimal("0.10"));
    assertThat(firstPayment.getCurrency()).isEqualTo(Currency.EUR);
    assertThat(firstPayment.getDescription()).isEqualTo("39910273027");
    assertThat(firstPayment.getRemitterIban()).isEqualTo("EE157700771001802057");
    assertThat(firstPayment.getRemitterIdCode()).isEqualTo("39910273027");
    assertThat(firstPayment.getBeneficiaryIban()).isEqualTo("EE442200221092874625");
    assertThat(firstPayment.getBeneficiaryIdCode()).isEqualTo("14118923");
    assertThat(firstPayment.getBeneficiaryName()).isEqualTo("TULEVA FONDID AS");
    assertThat(firstPayment.getExternalId()).isEqualTo("2025092900654847-1");
    assertThat(firstPayment.getReceivedAt()).isEqualTo(receivedAt);

    // Verify second payment (debit transaction)
    SavingFundPayment secondPayment = payments.get(1);
    assertThat(secondPayment.getAmount()).isEqualByComparingTo(new BigDecimal("-0.10"));
    assertThat(secondPayment.getCurrency()).isEqualTo(Currency.EUR);
    assertThat(secondPayment.getDescription()).isEqualTo("bounce-back");
    assertThat(secondPayment.getRemitterIban()).isEqualTo("EE442200221092874625");
    assertThat(secondPayment.getRemitterIdCode()).isEqualTo("14118923");
    assertThat(secondPayment.getRemitterName()).isEqualTo("TULEVA FONDID AS");
    assertThat(secondPayment.getBeneficiaryIban()).isEqualTo("EE157700771001802057");
    assertThat(secondPayment.getBeneficiaryIdCode()).isEqualTo(null);
    assertThat(secondPayment.getBeneficiaryName()).isEqualTo("Jüri Tamm");
    assertThat(secondPayment.getExternalId()).isEqualTo("2025092900673437-1");
    assertThat(secondPayment.getReceivedAt()).isEqualTo(receivedAt);
  }

  @Test
  void extractPayments_shouldHandleEmptyStatements() {
    // given
    List<String> entries = List.of(); // No entries
    String swedbankXmlResponse = createCamtXml(entries);
    JAXBElement<Document> response =
        marshaller.unMarshal(
            swedbankXmlResponse,
            JAXBElement.class,
            ee.swedbank.gateway.iso.response.report.ObjectFactory.class);
    Document document = response.getValue();
    Instant receivedAt = Instant.parse("2025-09-29T15:37:46Z");
    var statement = BankStatement.from(document.getBkToCstmrAcctRpt());

    // when
    List<SavingFundPayment> payments = extractor.extractPayments(statement, receivedAt);

    // then
    assertThat(payments).isEmpty();
  }

  @Test
  void extractPayments_shouldThrowExceptionWhenAccountHolderNameIsNull() {
    // given
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
        """
            .stripIndent();

    // when & then
    assertThatThrownBy(
            () -> {
              JAXBElement<Document> response =
                  marshaller.unMarshal(
                      xmlWithoutAccountHolderName,
                      JAXBElement.class,
                      ee.swedbank.gateway.iso.response.report.ObjectFactory.class);
              Document document = response.getValue();
              Instant receivedAt = Instant.parse("2025-09-29T15:37:46Z");
              var statement = BankStatement.from(document.getBkToCstmrAcctRpt());
              extractor.extractPayments(statement, receivedAt);
            })
        .isInstanceOf(PaymentProcessingException.class)
        .hasMessage("Bank statement account holder name not found");
  }

  @Test
  void extractPayments_shouldThrowExceptionWhenAccountHolderHasNoIdCodes() {
    // given
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
        """
            .stripIndent();

    // when & then
    assertThatThrownBy(
            () -> {
              JAXBElement<Document> response =
                  marshaller.unMarshal(
                      xmlWithoutIdCodes,
                      JAXBElement.class,
                      ee.swedbank.gateway.iso.response.report.ObjectFactory.class);
              Document document = response.getValue();
              Instant receivedAt = Instant.parse("2025-09-29T15:37:46Z");
              var statement = BankStatement.from(document.getBkToCstmrAcctRpt());
              extractor.extractPayments(statement, receivedAt);
            })
        .isInstanceOf(ee.tuleva.onboarding.swedbank.statement.BankStatementParseException.class)
        .hasMessageContaining("Expected exactly one account holder ID code, but found: 0");
  }

  @Test
  void extractPayments_shouldThrowExceptionWhenAccountHolderHasMultipleIdCodes() {
    // given
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
        """
            .stripIndent();

    // when & then
    assertThatThrownBy(
            () -> {
              JAXBElement<Document> response =
                  marshaller.unMarshal(
                      xmlWithMultipleIdCodes,
                      JAXBElement.class,
                      ee.swedbank.gateway.iso.response.report.ObjectFactory.class);
              Document document = response.getValue();
              Instant receivedAt = Instant.parse("2025-09-29T15:37:46Z");
              var statement = BankStatement.from(document.getBkToCstmrAcctRpt());
              extractor.extractPayments(statement, receivedAt);
            })
        .isInstanceOf(ee.tuleva.onboarding.swedbank.statement.BankStatementParseException.class)
        .hasMessageContaining("Expected exactly one account holder ID code, but found: 2");
  }

  @Test
  void extractPayments_shouldThrowExceptionForUnsupportedCurrency() {
    // given
    List<String> entries =
        List.of(
            """
      <Ntry>
        <NtryRef>test-ref</NtryRef>
        <Amt Ccy="USD">100.00</Amt>
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
                <Amt Ccy="USD">100.00</Amt>
              </InstdAmt>
              <TxAmt>
                <Amt Ccy="USD">100.00</Amt>
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
            <RmtInf>
              <Ustrd>Test payment</Ustrd>
            </RmtInf>
          </TxDtls>
        </NtryDtls>
      </Ntry>
      """
                .stripIndent());

    String swedbankXmlResponse = createCamtXml(entries);
    JAXBElement<Document> response =
        marshaller.unMarshal(
            swedbankXmlResponse,
            JAXBElement.class,
            ee.swedbank.gateway.iso.response.report.ObjectFactory.class);
    Document document = response.getValue();
    Instant receivedAt = Instant.parse("2025-09-29T15:37:46Z");

    // when & then
    assertThatThrownBy(
            () -> {
              var statement = BankStatement.from(document.getBkToCstmrAcctRpt());
              extractor.extractPayments(statement, receivedAt);
            })
        .isInstanceOf(PaymentProcessingException.class)
        .hasMessage("Bank transfer currency not supported: USD");
  }

  @Test
  void extractPayments_shouldThrowExceptionForMultipleEndToEndIds() {
    // given
    List<String> entries =
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
                <EndToEndId>first-id</EndToEndId>
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
              <RmtInf>
                <Ustrd>Test payment</Ustrd>
              </RmtInf>
            </TxDtls>
            <TxDtls>
              <Refs>
                <AcctSvcrRef>test-ref-2</AcctSvcrRef>
                <EndToEndId>second-id</EndToEndId>
              </Refs>
              <AmtDtls>
                <InstdAmt>
                  <Amt Ccy="EUR">50.00</Amt>
                </InstdAmt>
                <TxAmt>
                  <Amt Ccy="EUR">50.00</Amt>
                </TxAmt>
              </AmtDtls>
              <RltdPties>
                <Dbtr>
                  <Nm>Another Person</Nm>
                </Dbtr>
                <DbtrAcct>
                  <Id>
                    <IBAN>EE987654321098765432</IBAN>
                  </Id>
                </DbtrAcct>
              </RltdPties>
              <RmtInf>
                <Ustrd>Another payment</Ustrd>
              </RmtInf>
            </TxDtls>
          </NtryDtls>
        </Ntry>
        """
                .stripIndent());

    String swedbankXmlResponse = createCamtXml(entries);
    JAXBElement<Document> response =
        marshaller.unMarshal(
            swedbankXmlResponse,
            JAXBElement.class,
            ee.swedbank.gateway.iso.response.report.ObjectFactory.class);
    Document document = response.getValue();
    Instant receivedAt = Instant.parse("2025-09-29T15:37:46Z");

    // when & then
    assertThatThrownBy(
            () -> {
              var statement = BankStatement.from(document.getBkToCstmrAcctRpt());
              extractor.extractPayments(statement, receivedAt);
            })
        .isInstanceOf(ee.tuleva.onboarding.swedbank.statement.BankStatementParseException.class)
        .hasMessageContaining("Expected at most one end-to-end ID, but found: 2");
  }

  private String createCamtXml(List<String> entries) {
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
                  <PstlAdr>
                    <Ctry>EE</Ctry>
                    <AdrLine>LIIVALAIA 34, TALLINN, 15040</AdrLine>
                  </PstlAdr>
                  <Othr>
                    <Id>10060701</Id>
                    <SchmeNm>
                      <Cd>COID</Cd>
                    </SchmeNm>
                  </Othr>
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
                <NbOfNtries>1</NbOfNtries>
                <Sum>0.10</Sum>
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
}
