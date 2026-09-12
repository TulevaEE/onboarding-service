package ee.tuleva.onboarding.banking.payment;

import static ee.tuleva.onboarding.banking.payment.PaymentIntegrityCheck.UNSTRUCTURED_ADDRESS;
import static ee.tuleva.onboarding.banking.payment.PaymentIntegrityCheck.XSD_SCHEMA;
import static ee.tuleva.onboarding.banking.payment.PaymentIntegrityViolation.mismatch;
import static java.math.RoundingMode.HALF_UP;
import static javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD;
import static javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA;
import static javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI;

import java.io.StringReader;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

/**
 * Re-reads a generated pain.001 payment file and asserts it encodes exactly the PaymentRequest it
 * was built from. The generator writes XML by hand, so nothing else proves that the bytes we are
 * about to hand the bank say what we authorised.
 */
@Component
@RequiredArgsConstructor
@NullMarked
public class PaymentFileIntegrityValidator {

  private static final String XSD = "/banking/iso20022/pain.001.001.09.xsd";
  private static final int NAME_MAX_LENGTH = 70;
  private static final int ID_MAX_LENGTH = 35;
  private static final int DESCRIPTION_MAX_LENGTH = 140;
  private static final Schema SCHEMA = loadSchema();

  private final Clock clock;

  public List<PaymentIntegrityViolation> validate(String paymentXml, PaymentRequest request) {
    var violations = new ArrayList<PaymentIntegrityViolation>();
    validateSchema(paymentXml).ifPresent(violations::add);

    Document document;
    try {
      document = parse(paymentXml);
    } catch (Exception e) {
      violations.add(new PaymentIntegrityViolation(XSD_SCHEMA, "document"));
      return violations;
    }

    if (document.getElementsByTagName("AdrLine").getLength() > 0) {
      // SEB rejects unstructured addresses from 15.11.2026; a future address must use PstlAdr.
      violations.add(new PaymentIntegrityViolation(UNSTRUCTURED_ADDRESS, "address"));
    }

    violations.addAll(validateFields(document, request));
    return violations;
  }

  private List<PaymentIntegrityViolation> validateFields(
      Document document, PaymentRequest request) {
    var violations = new ArrayList<PaymentIntegrityViolation>();

    var initiation = children(document.getDocumentElement(), "CstmrCdtTrfInitn");
    if (initiation.size() != 1) {
      violations.add(mismatch("initiationCount"));
      return violations;
    }
    var header = children(initiation.getFirst(), "GrpHdr");
    var paymentInfos = children(initiation.getFirst(), "PmtInf");
    if (header.size() != 1 || paymentInfos.size() != 1) {
      violations.add(mismatch("paymentInfoCount"));
      return violations;
    }
    var paymentInfo = paymentInfos.getFirst();
    var transactions = children(paymentInfo, "CdtTrfTxInf");
    if (transactions.size() != 1) {
      violations.add(mismatch("transactionCount"));
      return violations;
    }
    var transaction = transactions.getFirst();
    var amount = request.amount().setScale(2, HALF_UP);

    check(violations, "groupTransactionCount", "1", text(header.getFirst(), "NbOfTxs"));
    checkAmount(violations, "groupControlSum", amount, text(header.getFirst(), "CtrlSum"));
    check(
        violations,
        "initiatingPartyName",
        truncate(request.remitterName(), NAME_MAX_LENGTH),
        text(header.getFirst(), "InitgPty", "Nm"));

    check(violations, "paymentTransactionCount", "1", text(paymentInfo, "NbOfTxs"));
    checkAmount(violations, "paymentControlSum", amount, text(paymentInfo, "CtrlSum"));
    checkExecutionDate(violations, text(paymentInfo, "ReqdExctnDt", "Dt"));
    check(
        violations,
        "remitterName",
        truncate(request.remitterName(), NAME_MAX_LENGTH),
        text(paymentInfo, "Dbtr", "Nm"));
    check(
        violations,
        "remitterId",
        truncate(request.remitterId(), ID_MAX_LENGTH),
        text(paymentInfo, "Dbtr", "Id", "OrgId", "Othr", "Id"));
    check(
        violations,
        "remitterIban",
        request.remitterIban(),
        text(paymentInfo, "DbtrAcct", "Id", "IBAN"));

    check(
        violations,
        "instructionId",
        truncate(request.ourId(), ID_MAX_LENGTH),
        text(transaction, "PmtId", "InstrId"));
    check(
        violations,
        "endToEndId",
        truncate(request.endToEndId(), ID_MAX_LENGTH),
        text(transaction, "PmtId", "EndToEndId"));
    checkAmount(violations, "amount", amount, text(transaction, "Amt", "InstdAmt"));
    check(
        violations,
        "currency",
        "EUR",
        element(transaction, "Amt", "InstdAmt").map(e -> e.getAttribute("Ccy")));
    check(
        violations,
        "beneficiaryName",
        truncate(request.beneficiaryName(), NAME_MAX_LENGTH),
        text(transaction, "Cdtr", "Nm"));
    check(
        violations,
        "beneficiaryIban",
        request.beneficiaryIban(),
        text(transaction, "CdtrAcct", "Id", "IBAN"));
    check(
        violations,
        "description",
        truncate(request.description(), DESCRIPTION_MAX_LENGTH),
        text(transaction, "RmtInf", "Ustrd"));

    return violations;
  }

  private void check(
      List<PaymentIntegrityViolation> violations,
      String field,
      String expected,
      Optional<String> actual) {
    if (!actual.map(expected::equals).orElse(false)) {
      violations.add(mismatch(field));
    }
  }

  private void checkAmount(
      List<PaymentIntegrityViolation> violations,
      String field,
      BigDecimal expected,
      Optional<String> actual) {
    var parsed = actual.flatMap(PaymentFileIntegrityValidator::parseAmount);
    if (!parsed.map(value -> value.compareTo(expected) == 0).orElse(false)) {
      violations.add(mismatch(field));
    }
  }

  // A back-dated execution date is silently accepted by the schema but changes when the bank moves
  // the money, so it is checked as a field even though it has no counterpart on the request.
  private void checkExecutionDate(
      List<PaymentIntegrityViolation> violations, Optional<String> actual) {
    var today = LocalDate.now(clock);
    var parsed = actual.flatMap(PaymentFileIntegrityValidator::parseDate);
    if (!parsed.map(date -> !date.isBefore(today)).orElse(false)) {
      violations.add(mismatch("executionDate"));
    }
  }

  private static Optional<BigDecimal> parseAmount(String value) {
    try {
      return Optional.of(new BigDecimal(value));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }

  private static Optional<LocalDate> parseDate(String value) {
    try {
      return Optional.of(LocalDate.parse(value));
    } catch (RuntimeException e) {
      return Optional.empty();
    }
  }

  private static String truncate(String value, int maxLength) {
    return value.length() > maxLength ? value.substring(0, maxLength) : value;
  }

  private static Optional<String> text(Element scope, String... path) {
    return element(scope, path).map(Node::getTextContent);
  }

  private static Optional<Element> element(Element scope, String... path) {
    Element current = scope;
    for (String name : path) {
      var matches = children(current, name);
      if (matches.size() != 1) {
        return Optional.empty();
      }
      current = matches.getFirst();
    }
    return Optional.of(current);
  }

  private static List<Element> children(Element parent, String name) {
    var matches = new ArrayList<Element>();
    var childNodes = parent.getChildNodes();
    for (int i = 0; i < childNodes.getLength(); i++) {
      var child = childNodes.item(i);
      if (child instanceof Element element && element.getTagName().equals(name)) {
        matches.add(element);
      }
    }
    return matches;
  }

  private Optional<PaymentIntegrityViolation> validateSchema(String paymentXml) {
    try {
      var validator = SCHEMA.newValidator();
      validator.setProperty(ACCESS_EXTERNAL_DTD, "");
      validator.setProperty(ACCESS_EXTERNAL_SCHEMA, "");
      validator.validate(new StreamSource(new StringReader(paymentXml)));
      return Optional.empty();
    } catch (Exception e) {
      return Optional.of(new PaymentIntegrityViolation(XSD_SCHEMA, "document"));
    }
  }

  @SneakyThrows
  private static Document parse(String paymentXml) {
    var factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);
    return factory.newDocumentBuilder().parse(new InputSource(new StringReader(paymentXml)));
  }

  @SneakyThrows
  private static Schema loadSchema() {
    var factory = SchemaFactory.newInstance(W3C_XML_SCHEMA_NS_URI);
    return factory.newSchema(
        new StreamSource(PaymentFileIntegrityValidator.class.getResourceAsStream(XSD)));
  }
}
