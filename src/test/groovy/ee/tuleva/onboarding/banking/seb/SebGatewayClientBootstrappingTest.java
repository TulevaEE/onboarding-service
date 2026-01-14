package ee.tuleva.onboarding.banking.seb;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.LoadDotEnv;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.net.ssl.SSLContext;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@LoadDotEnv
@TestPropertySource(
    properties = {
      "seb-gateway.enabled=true",
      "seb-gateway.url=https://localhost:8443",
      // See .env.sample for instructions on how to set these
      "seb-gateway.org-id=${SEB_GATEWAY_ORG_ID}",
      "seb-gateway.keystore.path=${SEB_GATEWAY_KEYSTORE_PATH}",
      "seb-gateway.keystore.password=${SEB_GATEWAY_KEYSTORE_PASSWORD}"
    })
@Import(LocalProxySebTlsStrategyFactory.class)
@Disabled(
"""
  Run manually to bootstrap test fixtures - requires proxying through a whitelisted IP.

  Tuleva EC2 instace can be used with AWS SSM:
  aws ssm start-session
    --target <INSTACE_ID>
    --document-name AWS-StartPortForwardingSessionToRemoteHost
    --parameters '{"host":["test.api.bgw.baltics.sebgroup.com"],"portNumber":["443"],"localPortNumber":["8443"]}'
    --region eu-central-1
    --profile <TULEVA_PROFILE>
""")
class SebGatewayClientBootstrappingTest {

  private static final String TEST_IBAN_EUR = "EE651010220306497226";
  private static final Path FIXTURES_DIR = Path.of("build/test-fixtures/banking/seb");

  @Autowired private SebGatewayClient sebGatewayClient;

  @BeforeAll
  static void setupFixturesDir() throws IOException {
    Files.createDirectories(FIXTURES_DIR);
  }

  @Test
  @DisplayName("Capture EOD transactions response")
  void captureEodTransactions() throws IOException {
    String xml = sebGatewayClient.getEodTransactions(TEST_IBAN_EUR);

    assertThat(xml).isNotNull().contains("camt.053");
    writeFixture("eod-transactions-response.xml", xml);
  }

  @Test
  @DisplayName("Capture current day transactions response")
  void captureCurrentTransactions() throws IOException {
    String xml = sebGatewayClient.getCurrentTransactions(TEST_IBAN_EUR);

    assertThat(xml).isNotNull().contains("camt.052");
    writeFixture("current-transactions-response.xml", xml);
  }

  @Test
  @DisplayName("Capture historical transactions response")
  void captureHistoricalTransactions() throws IOException {
    LocalDate dateTo = LocalDate.now().minusDays(1);
    LocalDate dateFrom = dateTo.minusDays(30);
    String xml = sebGatewayClient.getTransactions(TEST_IBAN_EUR, dateFrom, dateTo);

    assertThat(xml).isNotNull().contains("camt.053");
    writeFixture("historical-transactions-response.xml", xml);
  }

  @Test
  @DisplayName("Capture balances response")
  void captureBalances() throws IOException {
    String xml = sebGatewayClient.getBalances(TEST_IBAN_EUR);

    assertThat(xml).isNotNull().contains("camt.052").contains("<Bal>");
    writeFixture("balances-response.xml", xml);
  }

  @Test
  @DisplayName("Capture payment import response")
  void capturePaymentImport() throws IOException {
    String paymentXml = createTestPaymentXml();
    String response = sebGatewayClient.submitPaymentFile(paymentXml);

    assertThat(response).isNotNull().contains("importedFileId");
    writeFixture("imported-payment-response.xml", response);
  }

  private void writeFixture(String filename, String content) throws IOException {
    Path path = FIXTURES_DIR.resolve(filename);
    Files.writeString(path, prettyPrintXml(content));
    System.out.println("Written fixture: " + path.toAbsolutePath());
  }

  private String prettyPrintXml(String xml) {
    try {
      var normalized = xml.replaceAll(">\\s+<", "><");

      var factory = TransformerFactory.newInstance();
      var transformer = factory.newTransformer();
      transformer.setOutputProperty(OutputKeys.INDENT, "yes");
      transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

      var source = new StreamSource(new StringReader(normalized));
      var writer = new StringWriter();
      transformer.transform(source, new StreamResult(writer));
      return writer.toString();
    } catch (Exception e) {
      return xml;
    }
  }

  private String createTestPaymentXml() {
    String uniqueId = String.valueOf(System.currentTimeMillis());
    String executionDate = LocalDate.now().plusDays(1).toString();
    String creationDateTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pain.001.001.03">
          <CstmrCdtTrfInitn>
            <GrpHdr>
              <MsgId>BOOTSTRAP%s</MsgId>
              <CreDtTm>%s</CreDtTm>
              <NbOfTxs>1</NbOfTxs>
              <CtrlSum>1.00</CtrlSum>
              <InitgPty><Nm>BGW TESTCLIENT1</Nm></InitgPty>
            </GrpHdr>
            <PmtInf>
              <PmtInfId>PMT%s</PmtInfId>
              <PmtMtd>TRF</PmtMtd>
              <NbOfTxs>1</NbOfTxs>
              <CtrlSum>1.00</CtrlSum>
              <ReqdExctnDt>%s</ReqdExctnDt>
              <Dbtr><Nm>BGW TESTCLIENT1</Nm></Dbtr>
              <DbtrAcct><Id><IBAN>EE651010220306497226</IBAN></Id></DbtrAcct>
              <DbtrAgt><FinInstnId><BIC>EEUHEE2X</BIC></FinInstnId></DbtrAgt>
              <CdtTrfTxInf>
                <PmtId>
                  <InstrId>INSTR%s</InstrId>
                  <EndToEndId>E2E%s</EndToEndId>
                </PmtId>
                <Amt><InstdAmt Ccy="EUR">1.00</InstdAmt></Amt>
                <CdtrAgt><FinInstnId><BIC>EEUHEE2X</BIC></FinInstnId></CdtrAgt>
                <Cdtr><Nm>Test Recipient</Nm></Cdtr>
                <CdtrAcct><Id><IBAN>EE581010220306498225</IBAN></Id></CdtrAcct>
                <RmtInf><Ustrd>Bootstrap test payment</Ustrd></RmtInf>
              </CdtTrfTxInf>
            </PmtInf>
          </CstmrCdtTrfInitn>
        </Document>
        """
        .formatted(uniqueId, creationDateTime, uniqueId, executionDate, uniqueId, uniqueId);
  }
}

@TestComponent
@Primary
class LocalProxySebTlsStrategyFactory implements SebTlsStrategyFactory {
  @Override
  public TlsSocketStrategy create(SSLContext sslContext) {
    return new DefaultClientTlsStrategy(sslContext, NoopHostnameVerifier.INSTANCE);
  }
}
