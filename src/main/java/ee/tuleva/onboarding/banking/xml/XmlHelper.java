package ee.tuleva.onboarding.banking.xml;

import static java.math.RoundingMode.HALF_UP;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import lombok.SneakyThrows;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class XmlHelper {

  public static Document createDocument() {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      return factory.newDocumentBuilder().newDocument();
    } catch (ParserConfigurationException e) {
      throw new RuntimeException(e);
    }
  }

  public static Element add(Element parent, String name, String value) {
    Element element = add(parent, name);
    element.setTextContent(value);
    return element;
  }

  public static Element add(Element parent, String name, int value) {
    return add(parent, name, Integer.toString(value));
  }

  public static Element add(Element parent, String name, long value) {
    return add(parent, name, Long.toString(value));
  }

  public static Element add(Node parent, String name) {
    Element element =
        (parent instanceof Document ? (Document) parent : parent.getOwnerDocument())
            .createElement(name);
    parent.appendChild(element);
    return element;
  }

  public static Element add(Element parent, String name, String value, int maxLength) {
    if (value.length() > maxLength) value = value.substring(0, maxLength);
    return add(parent, name, value);
  }

  public static Element add(Element parent, String name, Date date) {
    return add(parent, name, new SimpleDateFormat("yyyy-MM-dd").format(date));
  }

  public static Element add(Element parent, String name, BigDecimal value) {
    return add(parent, name, value.setScale(2, HALF_UP).toPlainString());
  }

  @SneakyThrows
  public static String asString(Document document) {
    var transformer = transformerFactory().newTransformer();
    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
    var stringWriter = new StringWriter();
    transformer.transform(new DOMSource(document), new StreamResult(stringWriter));
    return stringWriter.toString();
  }

  public static TransformerFactory transformerFactory() {
    TransformerFactory factory = TransformerFactory.newInstance();
    try {
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    } catch (Exception ignored) {
    }
    try {
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
    } catch (Exception ignored) {
    }
    return factory;
  }
}
