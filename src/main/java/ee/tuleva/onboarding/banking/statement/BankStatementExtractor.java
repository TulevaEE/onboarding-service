package ee.tuleva.onboarding.banking.statement;

import ee.tuleva.onboarding.banking.xml.Iso20022Marshaller;
import jakarta.xml.bind.JAXBElement;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BankStatementExtractor {

  private final Iso20022Marshaller marshaller;

  public BankStatement extractFromIntraDayReport(String rawXml, ZoneId timezone) {
    if (rawXml == null || rawXml.isBlank()) {
      throw new BankStatementParseException("Raw XML is null or empty");
    }
    try {
      JAXBElement<ee.tuleva.onboarding.banking.iso20022.camt052.Document> response =
          marshaller.unMarshal(
              rawXml,
              JAXBElement.class,
              ee.tuleva.onboarding.banking.iso20022.camt052.ObjectFactory.class);

      if (response == null || response.getValue() == null) {
        throw new BankStatementParseException("Unmarshalled document is null");
      }

      ee.tuleva.onboarding.banking.iso20022.camt052.Document document = response.getValue();

      if (document.getBkToCstmrAcctRpt() == null) {
        throw new BankStatementParseException("BkToCstmrAcctRpt is missing in document");
      }

      BankStatement statement = BankStatement.from(document.getBkToCstmrAcctRpt(), timezone);
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

  public BankStatement extractFromHistoricStatement(String rawXml, ZoneId timezone) {
    if (rawXml == null || rawXml.isBlank()) {
      throw new BankStatementParseException("Raw XML is null or empty");
    }

    try {
      JAXBElement<ee.tuleva.onboarding.banking.iso20022.camt053.Document> response =
          marshaller.unMarshal(
              rawXml,
              JAXBElement.class,
              ee.tuleva.onboarding.banking.iso20022.camt053.ObjectFactory.class);

      if (response == null || response.getValue() == null) {
        throw new BankStatementParseException("Unmarshalled document is null");
      }

      ee.tuleva.onboarding.banking.iso20022.camt053.Document document = response.getValue();

      if (document.getBkToCstmrStmt() == null) {
        throw new BankStatementParseException("BkToCstmrStmt is missing in document");
      }

      BankStatement statement = BankStatement.from(document.getBkToCstmrStmt(), timezone);
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
