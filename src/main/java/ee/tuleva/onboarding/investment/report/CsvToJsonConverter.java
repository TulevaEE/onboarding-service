package ee.tuleva.onboarding.investment.report;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.input.BOMInputStream;
import org.springframework.stereotype.Component;

@Component
public class CsvToJsonConverter {

  public List<Map<String, Object>> convert(InputStream csvInputStream, char delimiter) {
    return convert(csvInputStream, delimiter, 0);
  }

  public List<Map<String, Object>> convert(
      InputStream csvInputStream, char delimiter, int headerRowIndex) {
    try {
      String csvContent = readAndSkipRows(csvInputStream, headerRowIndex);

      try (CSVParser parser =
          CSVFormat.DEFAULT
              .builder()
              .setHeader()
              .setDelimiter(delimiter)
              .setAllowMissingColumnNames(true)
              .setTrim(true)
              .setIgnoreEmptyLines(true)
              .build()
              .parse(new StringReader(csvContent))) {

        return parser.stream().map(this::recordToMap).toList();
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to convert CSV to JSON", e);
    }
  }

  private String readAndSkipRows(InputStream inputStream, int rowsToSkip) throws IOException {
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(
                BOMInputStream.builder().setInputStream(inputStream).get(),
                StandardCharsets.UTF_8))) {

      List<String> lines = reader.lines().toList();
      if (rowsToSkip >= lines.size()) {
        return "";
      }
      return lines.stream().skip(rowsToSkip).collect(Collectors.joining("\n"));
    }
  }

  private Map<String, Object> recordToMap(CSVRecord record) {
    Map<String, Object> map = new HashMap<>();
    record
        .toMap()
        .forEach(
            (key, value) -> {
              if (!key.isEmpty()) {
                map.put(key, parseValue(value));
              }
            });
    return map;
  }

  private Object parseValue(String value) {
    if (value == null || value.isEmpty()) {
      return null;
    }
    try {
      String normalized = value.replace(",", ".").replace(" ", "");
      return new BigDecimal(normalized);
    } catch (NumberFormatException e) {
      return value;
    }
  }
}
