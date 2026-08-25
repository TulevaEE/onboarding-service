package ee.tuleva.onboarding.banking.payment;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.SneakyThrows;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Reads the bank's own verdict on payments we submitted, out of a pain.002 status report.
 *
 * <p>Matched on local element names rather than a generated binding: the interesting part of the
 * message is three fields deep and stable across the 001.03 and 001.10 generations, and we do not
 * need the rest of the schema to act on a rejection.
 */
@Component
@NullMarked
public class PaymentStatusReportExtractor {

  @SneakyThrows
  public PaymentStatusReport extract(String xml) {
    var factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    var document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));

    var statuses = new ArrayList<PaymentStatusReport.TransactionStatus>();
    var transactions = document.getElementsByTagNameNS("*", "TxInfAndSts");
    for (int i = 0; i < transactions.getLength(); i++) {
      var transaction = (Element) transactions.item(i);
      var endToEndId = childText(transaction, "OrgnlEndToEndId");
      if (endToEndId == null) {
        continue;
      }
      var statusCode = childText(transaction, "TxSts");
      statuses.add(
          new PaymentStatusReport.TransactionStatus(
              endToEndId,
              statusCode == null ? PaymentStatus.UNKNOWN : PaymentStatus.from(statusCode),
              reasonCode(transaction)));
    }

    return new PaymentStatusReport(firstText(document.getDocumentElement(), "GrpSts"), statuses);
  }

  private static @Nullable String reasonCode(Element transaction) {
    var reasons = transaction.getElementsByTagNameNS("*", "Rsn");
    if (reasons.getLength() == 0) {
      return null;
    }
    return firstText((Element) reasons.item(0), "Cd");
  }

  /**
   * Direct-descendant lookup, so a nested original-transaction block cannot shadow the real one.
   */
  private static @Nullable String childText(Element parent, String localName) {
    NodeList children = parent.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node child = children.item(i);
      if (child.getNodeType() == Node.ELEMENT_NODE && localName.equals(child.getLocalName())) {
        return trimmed(child.getTextContent());
      }
    }
    return firstText(parent, localName);
  }

  private static @Nullable String firstText(Element parent, String localName) {
    var found = parent.getElementsByTagNameNS("*", localName);
    return found.getLength() == 0 ? null : trimmed(found.item(0).getTextContent());
  }

  private static @Nullable String trimmed(@Nullable String value) {
    if (value == null) {
      return null;
    }
    var trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  public List<PaymentStatusReport.TransactionStatus> rejections(PaymentStatusReport report) {
    return report.transactionStatuses().stream().filter(t -> t.status().isRejection()).toList();
  }
}
