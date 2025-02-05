package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import static java.time.ZoneOffset.UTC;
import static java.util.stream.StreamSupport.stream;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.fasterxml.jackson.databind.JsonNode;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Slf4j
@ToString(onlyExplicitlyIncluded = true)
public class NAVCheckValueRetriever implements ComparisonIndexRetriever {
  @ToString.Include public static final String KEY = "NAV_CHECK_VALUE";

  public static final List<String> FUND_TICKERS =
      List.of("0P000152G5.F", "0P0001N0Z0.F", "SGAS.DE", "SLMC.DE", "SGAJ.DE", "0P0001MGOG.F");

  private final RestClient restClient;

  public NAVCheckValueRetriever(RestClient.Builder restClientBuilder) {
    this.restClient = restClientBuilder.build();
  }

  @Override
  public String getKey() {
    return KEY;
  }

  @Override
  public List<FundValue> retrieveValuesForRange(LocalDate startDate, LocalDate endDate) {
    return FUND_TICKERS.stream()
        .map(fundName -> retrieveValuesForFund(fundName, startDate, endDate))
        .flatMap(List::stream)
        .toList();
  }

  private List<FundValue> retrieveValuesForFund(
      String fundName, LocalDate startDate, LocalDate endDate) {

    String fetchUri = buildFetchUri(fundName, startDate, endDate);

    JsonNode response =
        restClient.get().uri(fetchUri).accept(APPLICATION_JSON).retrieve().body(JsonNode.class);

    JsonNode resultNode = response.path("chart").path("result").path(0);

    List<LocalDate> timestamps = parseTimestamps(resultNode);
    List<BigDecimal> fundValues = parseFundValues(resultNode);

    if (fundValues.size() != timestamps.size()) {
      throw new IllegalStateException(
          "NAV checker response timestamp and fund values count do not match");
    }

    return IntStream.range(0, fundValues.size())
        .mapToObj(i -> new FundValue(fundName, timestamps.get(i), fundValues.get(i)))
        .toList();
  }

  private List<LocalDate> parseTimestamps(JsonNode resultNode) {
    return stream(resultNode.path("timestamp").spliterator(), false)
        .map(
            jsonNode -> {
              long timestamp = jsonNode.asLong();
              Instant instant = Instant.ofEpochSecond(timestamp);
              return instant.atZone(UTC).toLocalDate();
            })
        .toList();
  }

  private List<BigDecimal> parseFundValues(JsonNode resultNode) {
    JsonNode adjustedCloseNode =
        resultNode.path("indicators").path("adjclose").get(0).path("adjclose");

    return stream(adjustedCloseNode.spliterator(), false)
        .map(jsonNode -> BigDecimal.valueOf(jsonNode.asDouble()))
        .toList();
  }

  private String buildFetchUri(String fundName, LocalDate startDate, LocalDate endDate) {
    ZoneId utcZoneId = ZoneId.of("UTC");

    long startEpoch = startDate.atStartOfDay(utcZoneId).minusDays(1).toEpochSecond();
    long endEpoch = endDate.atStartOfDay(utcZoneId).plusDays(1).toEpochSecond();

    return UriComponentsBuilder.fromHttpUrl(
            "https://query1.finance.yahoo.com/v7/finance/chart/{ticker}")
        .query("interval=1d")
        .query("events=history")
        .query("includeAdjustedClose=true")
        .query("period1={startTime}")
        .query("period2={endTime}")
        .buildAndExpand(fundName, startEpoch, endEpoch)
        .toUriString();
  }
}
