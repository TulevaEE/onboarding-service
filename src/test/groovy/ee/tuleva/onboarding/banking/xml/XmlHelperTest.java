package ee.tuleva.onboarding.banking.xml;

import static javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD;
import static javax.xml.XMLConstants.ACCESS_EXTERNAL_STYLESHEET;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import javax.xml.transform.TransformerFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

class XmlHelperTest {

  @Test
  void add_intOverload_setsIntegerTextContentAndAttachesElement() {
    Document document = XmlHelper.createDocument();
    Element root = XmlHelper.add(document, "Root");

    Element element = XmlHelper.add(root, "Count", 5);

    assertThat(element.getTagName()).isEqualTo("Count");
    assertThat(element.getTextContent()).isEqualTo("5");
    assertThat(root.getFirstChild()).isSameAs(element);
  }

  @Test
  void add_longOverload_setsLongTextContentAndAttachesElement() {
    Document document = XmlHelper.createDocument();
    Element root = XmlHelper.add(document, "Root");

    Element element = XmlHelper.add(root, "Amount", 123456789012L);

    assertThat(element.getTagName()).isEqualTo("Amount");
    assertThat(element.getTextContent()).isEqualTo("123456789012");
    assertThat(root.getFirstChild()).isSameAs(element);
  }

  @Test
  void add_withMaxLength_doesNotTruncateWhenValueIsExactlyAtTheLimit() {
    Document document = XmlHelper.createDocument();
    Element root = XmlHelper.add(document, "Root");

    Element element = XmlHelper.add(root, "Info", "abcde", 5);

    assertThat(element.getTextContent()).isEqualTo("abcde");
  }

  @Test
  void add_withMaxLength_truncatesWhenValueExceedsTheLimit() {
    Document document = XmlHelper.createDocument();
    Element root = XmlHelper.add(document, "Root");

    Element element = XmlHelper.add(root, "Info", "abcdef", 5);

    assertThat(element.getTextContent()).isEqualTo("abcde");
  }

  @Test
  void add_withDate_formatsAsIsoLocalDate() {
    Document document = XmlHelper.createDocument();
    Element root = XmlHelper.add(document, "Root");
    Date date =
        Date.from(LocalDate.of(2026, 3, 5).atStartOfDay(ZoneId.systemDefault()).toInstant());

    Element element = XmlHelper.add(root, "Date", date);

    assertThat(element.getTagName()).isEqualTo("Date");
    assertThat(element.getTextContent()).isEqualTo("2026-03-05");
  }

  @Test
  void asString_indentsNestedElementsByTwoSpaces() {
    Document document = XmlHelper.createDocument();
    Element root = XmlHelper.add(document, "Root");
    XmlHelper.add(root, "Child", "value");

    String xml = XmlHelper.asString(document);

    assertThat(xml)
        .isEqualTo(
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
                + "<Root>\n"
                + "  <Child>value</Child>\n"
                + "</Root>\n");
  }

  @Test
  void transformerFactory_disablesExternalDtdAndStylesheetAccess() {
    TransformerFactory factory = XmlHelper.transformerFactory();

    assertThat(factory.getAttribute(ACCESS_EXTERNAL_DTD)).isEqualTo("");
    assertThat(factory.getAttribute(ACCESS_EXTERNAL_STYLESHEET)).isEqualTo("");
  }
}
