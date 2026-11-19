package ee.tuleva.onboarding.taxwin.income;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.UUID;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.client.core.WebServiceMessageCallback;
import org.springframework.ws.soap.SoapHeader;
import org.springframework.ws.soap.SoapMessage;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

record XRoadSoapHeaders(String clientId, String serviceId, String userId, UUID messageId)
    implements WebServiceMessageCallback {

  private static final String XROAD = "http://x-road.eu/xsd/xroad.xsd";
  private static final String IDENTIFIERS = "http://x-road.eu/xsd/identifiers";
  private static final String PROTOCOL_VERSION = "4.0";
  private static final List<String> SUBSYSTEM_PARTS =
      List.of("xRoadInstance", "memberClass", "memberCode", "subsystemCode");
  private static final List<String> SERVICE_PARTS =
      List.of(
          "xRoadInstance",
          "memberClass",
          "memberCode",
          "subsystemCode",
          "serviceCode",
          "serviceVersion");

  @Override
  public void doWithMessage(WebServiceMessage message) throws TransformerException {
    SoapHeader header =
        requireNonNull(((SoapMessage) message).getSoapHeader(), "SOAP message has no header");
    Document document = newDocument();
    Transformer transformer = TransformerFactory.newInstance().newTransformer();
    for (Element element :
        List.of(
            identifier(document, "client", "SUBSYSTEM", clientId, SUBSYSTEM_PARTS),
            identifier(document, "service", "SERVICE", serviceId, SERVICE_PARTS),
            text(document, "id", messageId.toString()),
            text(document, "userId", userId),
            text(document, "protocolVersion", PROTOCOL_VERSION))) {
      transformer.transform(new DOMSource(element), header.getResult());
    }
  }

  private static Element identifier(
      Document document, String name, String objectType, String id, List<String> partNames) {
    String[] parts = id.split("/");
    if (parts.length != partNames.size()) {
      throw new IllegalArgumentException(
          "Unexpected X-Road identifier: name=" + name + ", id=" + id);
    }
    Element element = document.createElementNS(XROAD, "xrd:" + name);
    element.setAttributeNS(IDENTIFIERS, "iden:objectType", objectType);
    for (int i = 0; i < parts.length; i++) {
      Element part = document.createElementNS(IDENTIFIERS, "iden:" + partNames.get(i));
      part.setTextContent(parts[i]);
      element.appendChild(part);
    }
    return element;
  }

  private static Element text(Document document, String name, String value) {
    Element element = document.createElementNS(XROAD, "xrd:" + name);
    element.setTextContent(value);
    return element;
  }

  private static Document newDocument() {
    try {
      var factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      return factory.newDocumentBuilder().newDocument();
    } catch (ParserConfigurationException e) {
      throw new IllegalStateException("Cannot create an XML document", e);
    }
  }
}
