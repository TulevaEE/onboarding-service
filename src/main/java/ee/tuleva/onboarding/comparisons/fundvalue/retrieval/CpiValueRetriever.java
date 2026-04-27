package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import static java.time.format.DateTimeFormatter.ofPattern;
import static org.springframework.http.HttpMethod.*;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import lombok.SneakyThrows;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@ToString(onlyExplicitlyIncluded = true)
public class CpiValueRetriever implements ComparisonIndexRetriever {
  @ToString.Include public static final String KEY = "CPI_ECOICOP2";
  public static final String PROVIDER = "EUROSTAT";
  private static final String SOURCE_URL =
      "https://ec.europa.eu/eurostat/api/dissemination/sdmx/2.1/data/prc_hicp_minr/M.I25.AP.EE/?format=TSV&compressed=true";
  private static final String HEADING_PREFIX = "freq,unit,coicop18,geo";
  private static final String DATA_ROW_PREFIX = "M,I25,AP,EE";

  private final RestTemplate restTemplate;
  private final Clock clock;

  @Override
  public String getKey() {
    return KEY;
  }

  @Override
  public Duration stalenessThreshold() {
    // Eurostat publishes monthly with a ~17-day lag (e.g. March data lands mid-April),
    // so the natural in-cycle staleness reaches ~75 days right before the next release.
    // 90 days = we've missed at least one full publication cycle.
    return Duration.ofDays(90);
  }

  public CpiValueRetriever(RestTemplateBuilder restTemplateBuilder, Clock clock) {
    this.restTemplate = restTemplateBuilder.build();
    this.clock = clock;
  }

  @Override
  public List<FundValue> retrieveValuesForRange(LocalDate startDate, LocalDate endDate) {
    List<FundValue> cpiValues = getCpiValues();
    return cpiValues.stream()
        .filter(
            fundValue -> {
              LocalDate date = fundValue.date();
              return (startDate.isBefore(date) || startDate.equals(date))
                  && (endDate.isAfter(date) || endDate.equals(date));
            })
        .toList();
  }

  private List<FundValue> getCpiValues() {
    ResponseExtractor<List<FundValue>> responseExtractor =
        response -> {
          try (GZIPInputStream gzipInputStream = new GZIPInputStream(response.getBody());
              BufferedReader reader = new BufferedReader(new InputStreamReader(gzipInputStream))) {
            return parseCpiValues(reader);
          }
        };

    return restTemplate.execute(SOURCE_URL, GET, null, responseExtractor);
  }

  @SneakyThrows
  private List<FundValue> parseCpiValues(BufferedReader reader) {
    List<String[]> lines = new ArrayList<>();
    String line;

    while ((line = reader.readLine()) != null) {
      if (Stream.of(HEADING_PREFIX, DATA_ROW_PREFIX).anyMatch(line::startsWith)) {
        lines.add(line.split("\t"));
      }
    }

    if (lines.size() < 2) {
      throw new EurostatImportException(
          "Eurostat response missing expected rows: url=" + SOURCE_URL + ", lines=" + lines.size());
    }

    String[] header = lines.get(0);
    String[] data = lines.get(1);
    List<FundValue> cpiValues = new ArrayList<>();
    var now = clock.instant();

    for (int i = 1; i < header.length && i < data.length; i++) {
      String yearMonth = header[i].trim();
      String rawValue = data[i].trim();

      if (rawValue.isEmpty() || rawValue.startsWith(":")) {
        continue;
      }

      String numericPart = rawValue.split("\\s+", 2)[0];
      LocalDate date = LocalDate.parse(yearMonth + "-01", ofPattern("yyyy-MM-dd"));

      try {
        BigDecimal cpiValue = new BigDecimal(numericPart);
        cpiValues.add(new FundValue(KEY, date, cpiValue, PROVIDER, now));
      } catch (NumberFormatException e) {
        log.error("Could not convert CPI value to BigDecimal: rawValue={}", rawValue);
      }
    }

    if (cpiValues.isEmpty()) {
      throw new EurostatImportException(
          "Eurostat response contained no parseable CPI values: url=" + SOURCE_URL);
    }

    return cpiValues;
  }
}
