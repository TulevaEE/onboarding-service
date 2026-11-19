package ee.tuleva.onboarding.banking.message;

import java.io.StringReader;
import java.util.Arrays;
import java.util.Optional;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import lombok.Getter;

public enum BankMessageType {
  INTRA_DAY_REPORT("camt.052.001.02"),
  HISTORIC_STATEMENT("camt.053.001.02");

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
    if (xml == null || xml.isBlank()) {
      throw new IllegalArgumentException("XML string cannot be null or empty");
    }
    try {
      XMLStreamReader reader =
          XMLInputFactory.newInstance().createXMLStreamReader(new StringReader(xml));
      try {
        while (reader.hasNext()) {
          if (reader.next() == XMLStreamConstants.START_ELEMENT) {
            String namespace = reader.getNamespaceURI();
            return (namespace != null && !namespace.isEmpty())
                ? Optional.of(namespace)
                : Optional.empty();
          }
        }
        throw new IllegalArgumentException("No root element found in XML");
      } finally {
        reader.close();
      }
    } catch (XMLStreamException e) {
      throw new IllegalArgumentException("Failed to parse XML: " + e.getMessage(), e);
    }
  }
}
