package ee.tuleva.onboarding.swedbank.statement;

import ee.tuleva.onboarding.swedbank.http.SwedbankGatewayMarshaller;
import jakarta.xml.bind.JAXBElement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SwedbankBankStatementExtractor {

  private final SwedbankGatewayMarshaller marshaller;

  public BankStatement extractFromIntraDayReport(String rawXml) {
    if (rawXml == null || rawXml.isBlank()) {
      throw new BankStatementParseException("Raw XML is null or empty");
    }
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

      BankStatement statement = BankStatement.from(document.getBkToCstmrAcctRpt());
      log.debug("Extracted intra-day report with {} entries", statement.getEntries().size());
      return statement;
    } catch (BankStatementParseException e) {
      throw e;
    } catch (Exception e) {
      log.error("Failed to extract bank statement from intra-day report", e);
      throw new BankStatementParseException(
          "Failed to extract bank statement from intra-day report", e);
    }
  }

  public BankStatement extractFromHistoricStatement(String rawXml) {
    if (rawXml == null || rawXml.isBlank()) {
      throw new BankStatementParseException("Raw XML is null or empty");
    }

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

      BankStatement statement = BankStatement.from(document.getBkToCstmrStmt());
      log.debug("Extracted historic statement with {} entries", statement.getEntries().size());
      return statement;
    } catch (BankStatementParseException e) {
      throw e;
    } catch (Exception e) {
      log.error("Failed to extract bank statement from historic statement", e);
      throw new BankStatementParseException(
          "Failed to extract bank statement from historic statement", e);
    }
  }
}
