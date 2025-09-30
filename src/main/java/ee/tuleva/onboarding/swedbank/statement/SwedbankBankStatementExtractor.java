package ee.tuleva.onboarding.swedbank.statement;

import ee.tuleva.onboarding.swedbank.http.SwedbankGatewayMarshaller;
import jakarta.xml.bind.JAXBElement;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SwedbankBankStatementExtractor {

  private static final String CAMT_052_NAMESPACE = "urn:iso:std:iso:20022:tech:xsd:camt.052.001.02";
  private static final String CAMT_053_NAMESPACE = "urn:iso:std:iso:20022:tech:xsd:camt.053.001.02";

  public static final Set<String> SUPPORTED_NAMESPACES =
      Set.of(CAMT_052_NAMESPACE, CAMT_053_NAMESPACE);

  private final SwedbankGatewayMarshaller marshaller;

  public BankStatement extractBankStatement(String rawXml, String namespace) {
    if (rawXml == null || rawXml.isBlank()) {
      throw new BankStatementParseException("Raw XML is null or empty");
    }

    if (namespace == null || namespace.isBlank()) {
      throw new BankStatementParseException("Namespace is null or empty");
    }

    if (!SUPPORTED_NAMESPACES.contains(namespace)) {
      throw new BankStatementParseException("Unsupported namespace: " + namespace);
    }

    try {
      if (CAMT_052_NAMESPACE.equals(namespace)) {
        return extractFromIntraDayReport(rawXml);
      } else if (CAMT_053_NAMESPACE.equals(namespace)) {
        return extractFromHistoricStatement(rawXml);
      } else {
        throw new BankStatementParseException("Unknown XML namespace: " + namespace);
      }
    } catch (BankStatementParseException e) {
      throw e;
    } catch (Exception e) {
      log.error("Failed to parse bank statement XML", e);
      throw new BankStatementParseException("Failed to parse bank statement XML", e);
    }
  }

  private BankStatement extractFromIntraDayReport(String rawXml) {
    try {
      JAXBElement<ee.swedbank.gateway.iso.response.report.Document> response =
          marshaller.unMarshal(
              rawXml,
              JAXBElement.class,
              ee.swedbank.gateway.iso.response.report.ObjectFactory.class);

      if (response == null || response.getValue() == null) {
        throw new BankStatementParseException("Unmarshalled document is null");
      }

      ee.swedbank.gateway.iso.response.report.Document document = response.getValue();

      if (document.getBkToCstmrAcctRpt() == null) {
        throw new BankStatementParseException("BkToCstmrAcctRpt is missing in document");
      }

      return BankStatement.from(document.getBkToCstmrAcctRpt());
    } catch (BankStatementParseException e) {
      throw e;
    } catch (Exception e) {
      log.error("Failed to extract bank statement from intra-day report", e);
      throw new BankStatementParseException(
          "Failed to extract bank statement from intra-day report", e);
    }
  }

  private BankStatement extractFromHistoricStatement(String rawXml) {
    try {
      JAXBElement<ee.swedbank.gateway.iso.response.statement.Document> response =
          marshaller.unMarshal(
              rawXml,
              JAXBElement.class,
              ee.swedbank.gateway.iso.response.statement.ObjectFactory.class);

      if (response == null || response.getValue() == null) {
        throw new BankStatementParseException("Unmarshalled document is null");
      }

      ee.swedbank.gateway.iso.response.statement.Document document = response.getValue();

      if (document.getBkToCstmrStmt() == null) {
        throw new BankStatementParseException("BkToCstmrStmt is missing in document");
      }

      return BankStatement.from(document.getBkToCstmrStmt());
    } catch (BankStatementParseException e) {
      throw e;
    } catch (Exception e) {
      log.error("Failed to extract bank statement from historic statement", e);
      throw new BankStatementParseException(
          "Failed to extract bank statement from historic statement", e);
    }
  }
}
