package ee.tuleva.onboarding.taxwin.income;

import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.ws.test.client.RequestMatchers.anything;
import static org.springframework.ws.test.client.RequestMatchers.validPayload;
import static org.springframework.ws.test.client.RequestMatchers.xpath;
import static org.springframework.ws.test.client.ResponseCreators.withException;
import static org.springframework.ws.test.client.ResponseCreators.withPayload;
import static org.springframework.ws.test.client.ResponseCreators.withSoapEnvelope;

import ee.tuleva.onboarding.taxwin.DeclaredMonth;
import ee.tuleva.onboarding.taxwin.IncomeWindow;
import ee.tuleva.onboarding.taxwin.InvalidTaxDataConsentException;
import ee.tuleva.onboarding.taxwin.TaxDataRequestRejectedException;
import ee.tuleva.onboarding.taxwin.TaxDataUnavailableException;
import ee.tuleva.onboarding.taxwin.TaxHeadroomProperties;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Spliterators;
import java.util.stream.StreamSupport;
import javax.xml.transform.dom.DOMSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.SoapHeaderElement;
import org.springframework.ws.soap.SoapMessage;
import org.springframework.ws.test.client.MockWebServiceServer;
import org.springframework.ws.test.client.RequestMatcher;
import org.springframework.xml.transform.StringSource;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

class SoapTaxCertificateClientTest {

  private static final TaxHeadroomProperties PROPERTIES =
      new TaxHeadroomProperties(
          "http://xroad.test",
          "ee-dev/COM/14118923/tuleva-fund-management",
          "ee-dev/GOV/70000349/mkrliides/TaxCertificate/v2",
          "ee-dev/GOV/70006317/consent/consent-stage",
          "isiku_maksuandmed");
  private static final IncomeWindow WINDOW =
      new IncomeWindow(YearMonth.of(2025, 11), YearMonth.of(2026, 10));
  private static final Map<String, String> NAMESPACES = Map.of("tns", "http://emta-v6.x-road.eu");

  private final TaxDataConfiguration configuration = new TaxDataConfiguration();
  private final WebServiceTemplate template = configuration.taxDataWebServiceTemplate(PROPERTIES);
  private final MockWebServiceServer server = MockWebServiceServer.createServer(template);
  private final SoapTaxCertificateClient client =
      new SoapTaxCertificateClient(template, PROPERTIES);

  @AfterEach
  void verifyAllRequestsWereSent() {
    server.verify();
  }

  @Test
  void asksForTheLastTwelveMonthsAsTheLoggedInPersonAndReadsTheirDeclaredIncome()
      throws IOException {
    server
        .expect(validPayload(new ClassPathResource("taxwin/taxcertificate_v2.xsd")))
        .andExpect(xpath("/tns:TaxCertificate/request/code", NAMESPACES).evaluatesTo("38888888888"))
        .andExpect(
            xpath("/tns:TaxCertificate/request/consentReference", NAMESPACES)
                .evaluatesTo("consent-reference-1"))
        .andExpect(
            xpath("/tns:TaxCertificate/request/periodStart", NAMESPACES).evaluatesTo("11.2025"))
        .andExpect(
            xpath("/tns:TaxCertificate/request/periodEnd", NAMESPACES).evaluatesTo("10.2026"))
        .andExpect(
            headers(
                Map.of(
                    "client", "SUBSYSTEM ee-dev COM 14118923 tuleva-fund-management",
                    "service", "SERVICE ee-dev GOV 70000349 mkrliides TaxCertificate v2",
                    "userId", "EE38888888888",
                    "protocolVersion", "4.0")))
        .andRespond(withPayload(new ClassPathResource("taxwin/tax-certificate-response.xml")));

    var income = client.declaredIncome("38888888888", "consent-reference-1", WINDOW);

    assertThat(income)
        .hasSize(12)
        .containsEntry(
            YearMonth.of(2025, 11),
            new DeclaredMonth(new BigDecimal("2500.00"), new BigDecimal("90.00")));
  }

  @Test
  void aWithdrawnOrExpiredConsentIsRejectedAsAnInvalidConsent() {
    server.expect(anything()).andRespond(withSoapEnvelope(fault("INVALID_CONSENT", "Expired")));

    assertThatThrownBy(() -> client.declaredIncome("38888888888", "consent-reference-1", WINDOW))
        .isInstanceOf(InvalidTaxDataConsentException.class);
  }

  @Test
  void anyOtherFaultIsARejectedRequest() {
    server
        .expect(anything())
        .andRespond(withSoapEnvelope(fault("INVALID_PERIOD", "Period is too long")));

    assertThatThrownBy(() -> client.declaredIncome("38888888888", "consent-reference-1", WINDOW))
        .isInstanceOf(TaxDataRequestRejectedException.class);
  }

  @Test
  void aFaultFromTheSecurityServerRatherThanTheTaxDataServiceMeansTheServiceIsUnavailable() {
    server
        .expect(anything())
        .andRespond(
            withSoapEnvelope(
                new StringSource(
                    """
                    <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
                      <SOAP-ENV:Body>
                        <SOAP-ENV:Fault>
                          <faultcode>SOAP-ENV:Server.ServerProxy.ServiceFailed</faultcode>
                          <faultstring>Service is not available</faultstring>
                          <detail><faultDetail>8c4b1d3e</faultDetail></detail>
                        </SOAP-ENV:Fault>
                      </SOAP-ENV:Body>
                    </SOAP-ENV:Envelope>
                    """)));

    assertThatThrownBy(() -> client.declaredIncome("38888888888", "consent-reference-1", WINDOW))
        .isInstanceOf(TaxDataUnavailableException.class);
  }

  @Test
  void retriesOnceWhenTheSecurityServerCannotBeReachedAndThenGivesUp() {
    server.expect(anything()).andRespond(withException(new IOException("connection refused")));
    server.expect(anything()).andRespond(withException(new IOException("connection refused")));

    assertThatThrownBy(() -> client.declaredIncome("38888888888", "consent-reference-1", WINDOW))
        .isInstanceOf(TaxDataUnavailableException.class);
  }

  private static RequestMatcher headers(Map<String, String> expected) {
    return (uri, message) -> {
      var header = Objects.requireNonNull(((SoapMessage) message).getSoapHeader());
      Map<String, String> actual =
          StreamSupport.stream(
                  Spliterators.spliteratorUnknownSize(header.examineAllHeaderElements(), 0), false)
              .collect(
                  toMap(
                      element -> element.getName().getLocalPart(),
                      SoapTaxCertificateClientTest::describe,
                      (first, second) -> first,
                      HashMap::new));
      assertThat(actual).containsAllEntriesOf(expected).containsKey("id");
    };
  }

  private static String describe(SoapHeaderElement element) {
    var node = (Element) ((DOMSource) element.getSource()).getNode();
    var objectType = node.getAttributeNS("http://x-road.eu/xsd/identifiers", "objectType");
    var children = new StringBuilder(objectType);
    for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
      children.append(children.isEmpty() ? "" : " ").append(child.getTextContent());
    }
    return children.toString();
  }

  private static StringSource fault(String type, String message) {
    return new StringSource(
        """
        <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
          <SOAP-ENV:Body>
            <SOAP-ENV:Fault>
              <faultcode>SOAP-ENV:Client</faultcode>
              <faultstring>%2$s</faultstring>
              <detail>
                <tns:error xmlns:tns="http://emta-v6.x-road.eu">
                  <source>TaxCertificate</source>
                  <type>%1$s</type>
                  <message>%2$s</message>
                </tns:error>
              </detail>
            </SOAP-ENV:Fault>
          </SOAP-ENV:Body>
        </SOAP-ENV:Envelope>
        """
            .formatted(type, message));
  }
}
