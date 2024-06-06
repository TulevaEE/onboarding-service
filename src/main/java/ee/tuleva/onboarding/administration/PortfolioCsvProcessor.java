package ee.tuleva.onboarding.administration;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PortfolioCsvProcessor {

  private final PortfolioAnalyticsRepository repository;

  public void process(LocalDate date, InputStream csvInputStream) {

    try (CSVParser parser =
        CSVFormat.DEFAULT
            .builder()
            .setHeader()
            .setDelimiter(';')
            .setAllowMissingColumnNames(true)
            .setTrim(true)
            .setIgnoreEmptyLines(true)
            .build()
            .parse(new InputStreamReader(csvInputStream, StandardCharsets.UTF_8))) {

      List<Map<String, Object>> recordsList = new ArrayList<>();
      for (CSVRecord record : parser) {
        Map<String, Object> map = new HashMap<>();
        record
            .toMap()
            .forEach(
                (key, value) -> {
                  if (!key.isEmpty()) {
                    map.put(key, tryParseBigDecimal(value));
                  }
                });
        recordsList.add(map);
      }

      PortfolioAnalytics analytics =
          repository
              .findByDate(date)
              .orElseGet(() -> PortfolioAnalytics.builder().date(date).build());

      analytics.setContent(recordsList);
      repository.save(analytics);

    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private Object tryParseBigDecimal(String value) {
    try {
      return new BigDecimal(value);
    } catch (NumberFormatException e) {
      return value;
    }
  }
}
