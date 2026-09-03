package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.ingest.HistoricalRegistryValueParser.decimalSeparatorFor;

import ee.tuleva.onboarding.investment.transaction.HistoricalImportFormatException;
import ee.tuleva.onboarding.investment.transaction.HistoricalImportResult.RowError;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class HistoricalRegistryCsvParser {

  static final List<String> REQUIRED_HEADERS =
      List.of(
          "order_id",
          "fund_isin",
          "instrument_isin",
          "order_timestamp",
          "order_status",
          "expected_settlement_date",
          "comment");

  private final HistoricalRegistryRowParser rowParser;

  ParseResult parse(String csv) {
    char delimiter = sniffDelimiter(csv);
    char decimalSeparator = decimalSeparatorFor(delimiter);
    List<CSVRecord> records = parseRecords(csv, delimiter);
    List<RowError> errors = new ArrayList<>();
    List<ParsedRow> parsedRows = parseRows(records, errors, decimalSeparator);
    return new ParseResult(records.size(), parsedRows, errors);
  }

  private List<CSVRecord> parseRecords(String csv, char delimiter) {
    try (CSVParser parser =
        CSVFormat.DEFAULT
            .builder()
            .setHeader()
            .setDelimiter(delimiter)
            .setTrim(true)
            .setIgnoreEmptyLines(true)
            .get()
            .parse(new StringReader(csv))) {
      List<String> headers = new ArrayList<>();
      for (String header : parser.getHeaderNames()) {
        headers.add(normalize(header));
      }
      List<String> missingHeaders =
          REQUIRED_HEADERS.stream().filter(required -> !headers.contains(required)).toList();
      if (!missingHeaders.isEmpty()) {
        throw new HistoricalImportFormatException(missingHeaders, REQUIRED_HEADERS);
      }
      return parser.getRecords();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to parse historical registry CSV", e);
    }
  }

  private char sniffDelimiter(String csv) {
    String headerLine = csv.lines().findFirst().orElse("");
    return headerLine.contains(";") ? ';' : ',';
  }

  private String normalize(String header) {
    return header.strip().toLowerCase();
  }

  private List<ParsedRow> parseRows(
      List<CSVRecord> records, List<RowError> errors, char decimalSeparator) {
    List<ParsedRow> parsedRows = new ArrayList<>();
    Set<UUID> seenOrderUuids = new HashSet<>();
    Set<String> seenBrokerTransactionIds = new HashSet<>();
    for (CSVRecord record : records) {
      int rowNumber = (int) record.getRecordNumber() + 1;
      try {
        ParsedRow row = rowParser.parseRow(rowNumber, record, decimalSeparator);
        if (!seenOrderUuids.add(row.orderUuid())) {
          throw new RowParseException("Duplicate order_id in file: orderId=" + row.orderId());
        }
        if (row.brokerTransactionId() != null
            && !seenBrokerTransactionIds.add(row.brokerTransactionId())) {
          throw new RowParseException(
              "Duplicate brokerTransactionId in file: brokerTransactionId="
                  + row.brokerTransactionId());
        }
        parsedRows.add(row);
      } catch (RowParseException e) {
        errors.add(new RowError(rowNumber, Objects.requireNonNull(e.getMessage())));
      }
    }
    return parsedRows;
  }

  record ParseResult(int rowCount, List<ParsedRow> rows, List<RowError> errors) {}
}
