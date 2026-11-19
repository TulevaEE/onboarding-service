package ee.tuleva.onboarding.banking.message;

import static java.util.function.Predicate.not;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

import java.io.StringReader;
import java.util.Arrays;
import java.util.Optional;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import lombok.Getter;

public enum BankMessageType {
  INTRA_DAY_REPORT("camt.052.001.02"),
  HISTORIC_STATEMENT("camt.053.001.02");

  private static final XMLInputFactory XML_INPUT_FACTORY = xmlInputFactoryWithoutDtds();

  @Getter private final String xmlType;

  BankMessageType(String xmlType) {
    this.xmlType = xmlType;
  }

  public static BankMessageType ofMessage(String xml) {
    String namespace = rootNamespace(xml).orElseThrow();
    String messageName = namespace.substring(namespace.lastIndexOf(':') + 1);
    return Arrays.stream(values())
        .filter(type -> type.xmlType.equals(messageName))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Cannot find XML message type"));
  }

  private static Optional<String> rootNamespace(String xml) {
    try {
      XMLStreamReader reader = XML_INPUT_FACTORY.createXMLStreamReader(new StringReader(xml));
      try {
        skipToTheRootElement(reader);
        return Optional.ofNullable(reader.getNamespaceURI()).filter(not(String::isEmpty));
      } finally {
        reader.close();
      }
    } catch (XMLStreamException e) {
      throw new IllegalArgumentException("Failed to parse XML: " + e.getMessage(), e);
    }
  }

  private static void skipToTheRootElement(XMLStreamReader reader) throws XMLStreamException {
    int event = reader.next();
    while (event != START_ELEMENT) {
      event = reader.next();
    }
  }

  private static XMLInputFactory xmlInputFactoryWithoutDtds() {
    XMLInputFactory factory = XMLInputFactory.newInstance();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    return factory;
  }
}
