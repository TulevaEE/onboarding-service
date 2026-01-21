package ee.tuleva.onboarding.banking.payment;

import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

class PaymentMessageGeneratorTest {

  Clock clock = Clock.fixed(Instant.parse("2020-01-01T14:13:15Z"), UTC);
  PaymentMessageGenerator generator = new PaymentMessageGenerator(clock);

  @Test
  void generate() throws IOException, SAXException {
    var paymentRequest =
        PaymentRequest.builder()
            .remitterName("Tuleva AS")
            .remitterId("14118923")
            .remitterIban("EE121283519985595614")
            .beneficiaryName("John Doe")
            .beneficiaryIban("EE461277288334943840")
            .amount(new BigDecimal("111.03"))
            .description("money for nothing")
            .ourId("111")
            .endToEndId("123ABC")
            .build();

    var result = generator.generatePaymentMessage(paymentRequest, "HABAEE2X");

    assertThat(result).isEqualToIgnoringWhitespace(xml);

    var schema =
        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
            .newSchema(
                new StreamSource(
                    getClass().getResourceAsStream("/banking/iso20022/pain.001.001.09.xsd")));
    var validator = schema.newValidator();
    validator.validate(new StreamSource(new StringReader(result)));
  }

  // language=xml
  private static final String xml =
"""
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pain.001.001.09" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <CstmrCdtTrfInitn>
    <GrpHdr>
      <MsgId>1577887995</MsgId>
      <CreDtTm>2020-01-01T14:13:15Z</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
      <CtrlSum>111.03</CtrlSum>
      <InitgPty>
        <Nm>Tuleva AS</Nm>
      </InitgPty>
    </GrpHdr>
    <PmtInf>
      <PmtInfId>1577887995</PmtInfId>
      <PmtMtd>TRF</PmtMtd>
      <NbOfTxs>1</NbOfTxs>
      <CtrlSum>111.03</CtrlSum>
      <PmtTpInf>
        <SvcLvl>
          <Cd>SEPA</Cd>
        </SvcLvl>
      </PmtTpInf>
      <ReqdExctnDt>
        <Dt>2020-01-01</Dt>
      </ReqdExctnDt>
      <Dbtr>
        <Nm>Tuleva AS</Nm>
        <Id>
          <OrgId>
            <Othr>
              <Id>14118923</Id>
            </Othr>
          </OrgId>
        </Id>
      </Dbtr>
      <DbtrAcct>
        <Id>
          <IBAN>EE121283519985595614</IBAN>
        </Id>
      </DbtrAcct>
      <DbtrAgt>
        <FinInstnId>
          <BICFI>HABAEE2X</BICFI>
        </FinInstnId>
      </DbtrAgt>
      <CdtTrfTxInf>
        <PmtId>
          <InstrId>111</InstrId>
          <EndToEndId>123ABC</EndToEndId>
        </PmtId>
        <Amt>
          <InstdAmt Ccy="EUR">111.03</InstdAmt>
        </Amt>
        <Cdtr>
          <Nm>John Doe</Nm>
        </Cdtr>
        <CdtrAcct>
          <Id>
            <IBAN>EE461277288334943840</IBAN>
          </Id>
        </CdtrAcct>
        <RmtInf>
          <Ustrd>money for nothing</Ustrd>
        </RmtInf>
      </CdtTrfTxInf>
    </PmtInf>
  </CstmrCdtTrfInitn>
</Document>
""";
}
