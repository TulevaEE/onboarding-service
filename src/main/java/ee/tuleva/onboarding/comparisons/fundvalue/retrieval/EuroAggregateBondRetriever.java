package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import static org.springframework.http.MediaType.APPLICATION_JSON;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

// Bond leg of the world-market composite benchmark: the pension fund equity cap was lifted on
// 2019-09-02, so this series is closed history and is backfilled exactly once.
@Component
public class EuroAggregateBondRetriever implements ComparisonIndexRetriever {

  public static final String KEY = "EURO_AGGREGATE_BOND";

  static final LocalDate SERIES_START = LocalDate.of(2016, 6, 1);
  static final LocalDate SERIES_END = LocalDate.of(2019, 9, 9);

  private static final String PROVIDER = "YAHOO";
  private static final String TICKER = "IEAG.AS";

  private final RestClient restClient;
  private final Clock clock;

  public EuroAggregateBondRetriever(RestClient.Builder restClientBuilder, Clock clock) {
    this.restClient = restClientBuilder.build();
    this.clock = clock;
  }

  @Override
  public String getKey() {
    return KEY;
  }

  @Override
  public Duration stalenessThreshold() {
    return Duration.ofDays(365_000);
  }

  @Override
  public List<FundValue> retrieveValuesForRange(LocalDate startDate, LocalDate endDate) {
    LocalDate start = startDate.isBefore(SERIES_START) ? SERIES_START : startDate;
    LocalDate end = endDate.isAfter(SERIES_END) ? SERIES_END : endDate;
    if (start.isAfter(end)) {
      return List.of();
    }
    JsonNode response =
        restClient
            .get()
            .uri(fetchUri(start, end))
            .header("User-Agent", "Mozilla/5.0")
            .accept(APPLICATION_JSON)
            .retrieve()
            .body(JsonNode.class);
    return parseChart(response, start, end, clock.instant());
  }

  private static String fetchUri(LocalDate start, LocalDate end) {
    return "https://query1.finance.yahoo.com/v8/finance/chart/"
        + TICKER
        + "?period1="
        + epochSeconds(start)
        + "&period2="
        + epochSeconds(end.plusDays(1))
        + "&interval=1d";
  }

  private static long epochSeconds(LocalDate date) {
    return date.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
  }

  private static List<FundValue> parseChart(
      JsonNode response, LocalDate start, LocalDate end, Instant now) {
    JsonNode result = response.path("chart").path("result").path(0);
    JsonNode timestamps = result.path("timestamp");
    JsonNode adjclose = result.path("indicators").path("adjclose").path(0).path("adjclose");
    return IntStream.range(0, timestamps.size())
        .filter(i -> adjclose.path(i).isNumber())
        .mapToObj(
            i ->
                new FundValue(
                    KEY,
                    LocalDate.ofInstant(
                        Instant.ofEpochSecond(timestamps.get(i).asLong()), ZoneOffset.UTC),
                    BigDecimal.valueOf(adjclose.get(i).asDouble()),
                    PROVIDER,
                    now))
        .filter(value -> !value.date().isBefore(start) && !value.date().isAfter(end))
        .toList();
  }
}
