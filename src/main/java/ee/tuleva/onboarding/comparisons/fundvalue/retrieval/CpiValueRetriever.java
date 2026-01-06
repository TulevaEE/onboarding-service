package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import static java.time.format.DateTimeFormatter.ofPattern;
import static org.springframework.http.HttpMethod.*;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import lombok.SneakyThrows;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@ToString(onlyExplicitlyIncluded = true)
public class CpiValueRetriever implements ComparisonIndexRetriever {
  @ToString.Include public static final String KEY = "CPI";
  public static final String PROVIDER = "EUROSTAT";
  private static final String SOURCE_URL =
      "https://ec.europa.eu/eurostat/api/dissemination/sdmx/2.1/data/prc_hicp_midx/?format=TSV&compressed=true";

  private final RestTemplate restTemplate;

  @Override
  public String getKey() {
    return KEY;
  }

  public CpiValueRetriever(RestTemplateBuilder restTemplateBuilder) {
    this.restTemplate = restTemplateBuilder.build();
  }

  @Override
  public List<FundValue> retrieveValuesForRange(LocalDate startDate, LocalDate endDate) {
    List<FundValue> cpiValues = getCPIValues();
    return cpiValues.stream()
        .filter(
            fundValue -> {
              LocalDate date = fundValue.date();
              return (startDate.isBefore(date) || startDate.equals(date))
                  && (endDate.isAfter(date) || endDate.equals(date));
            })
        .toList();
  }

  private List<FundValue> getCPIValues() {
    ResponseExtractor<List<FundValue>> responseExtractor =
        response -> {
          try (GZIPInputStream gzipInputStream = new GZIPInputStream(response.getBody());
              BufferedReader reader = new BufferedReader(new InputStreamReader(gzipInputStream))) {
            return parseCPIValues(reader);
          }
        };

    return restTemplate.execute(SOURCE_URL, GET, null, responseExtractor);
  }

  @SneakyThrows
  private List<FundValue> parseCPIValues(BufferedReader reader) {
    String headingLine = "freq,unit,coicop,geo";
    String indexEE = "M,I96,CP00,EE";
    List<String[]> lines = new ArrayList<>();
    String line;

    while ((line = reader.readLine()) != null) {
      if (Stream.of(headingLine, indexEE).anyMatch(line::startsWith)) {
        lines.add(line.split("\t"));
      }
    }

    String[][] table = new String[lines.size()][0];
    lines.toArray(table);

    List<FundValue> cpiValues = new ArrayList<>();
    var now = Instant.now();

    for (int i = 0; i < table[0].length - 1; i++) {
      String yearMonth = table[0][1 + i].trim();
      String cpi = table[1][1 + i].trim();

      LocalDate date = LocalDate.parse(yearMonth + "-01", ofPattern("yyyy-MM-dd"));

      if (cpi.startsWith(":")) {
        continue;
      }

      try {
        BigDecimal cpiValue = new BigDecimal(cpi);
        cpiValues.add(new FundValue(KEY, date, cpiValue, PROVIDER, now));
      } catch (NumberFormatException e) {
        log.error("Could not convert CPI value to BigDecimal: {}", cpi);
      }
    }

    return cpiValues;
  }
}
