package ee.tuleva.onboarding.investment.report;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

@Component
public class CsvToJsonConverter {

  public List<Map<String, Object>> convert(InputStream csvInputStream, char delimiter) {
    try (CSVParser parser =
        CSVFormat.DEFAULT
            .builder()
            .setHeader()
            .setDelimiter(delimiter)
            .setAllowMissingColumnNames(true)
            .setTrim(true)
            .setIgnoreEmptyLines(true)
            .build()
            .parse(new InputStreamReader(csvInputStream, StandardCharsets.UTF_8))) {

      return parser.stream().map(this::recordToMap).toList();

    } catch (IOException e) {
      throw new RuntimeException("Failed to convert CSV to JSON", e);
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
