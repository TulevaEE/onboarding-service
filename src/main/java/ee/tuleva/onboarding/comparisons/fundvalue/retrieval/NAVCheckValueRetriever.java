package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.fasterxml.jackson.databind.JsonNode;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Slf4j
@RequiredArgsConstructor
public class NAVCheckValueRetriever implements ComparisonIndexRetriever {
  public static final String KEY = "NAV_CHECK_VALUE";

  public static final List<String> FUND_NAMES =
      Arrays.asList("0P000152G5", "0P0001N0Z0", "SGAS.DE", "SLMC.DE", "SGAJ.DE", "0P0001MGOG");

  private final RestClient.Builder restClientBuilder;

  @Override
  public String getKey() {
    return KEY;
  }

  @Override
  public List<FundValue> retrieveValuesForRange(LocalDate startDate, LocalDate endDate) {
    return FUND_NAMES.stream()
        .map(fundName -> retrieveValuesForFund(fundName, startDate, endDate))
        .flatMap(List::stream)
        .toList();
  }

  private List<FundValue> retrieveValuesForFund(
      String fundName, LocalDate startDate, LocalDate endDate) {

    RestClient restClient = restClientBuilder.build();

    String fetchUri = buildFetchUri(fundName, startDate, endDate);

    // TODO in parallel for all funds?
    JsonNode response =
        restClient.get().uri(fetchUri).accept(APPLICATION_JSON).retrieve().body(JsonNode.class);

    JsonNode resultNode = response.path("chart").path("result").path(0);

    List<LocalDate> timestamps = parseTimestamps(resultNode);
    List<BigDecimal> fundValues = parseFundValues(resultNode);

    if (fundValues.size() != timestamps.size()) {
      throw new RuntimeException(
          "NAV checker response timestamp and fund values count do not match");
    }

    return IntStream.range(0, fundValues.size())
        .mapToObj(i -> new FundValue(fundName, timestamps.get(i), fundValues.get(i)))
        .toList();
  }

  private List<LocalDate> parseTimestamps(JsonNode resultNode) {
    ZoneId utcZoneId = ZoneId.of("UTC");

    List<LocalDate> timestamps = new ArrayList<>();

    for (Iterator<JsonNode> it = resultNode.path("timestamp").elements(); it.hasNext(); ) {
      long timestamp = it.next().asLong();
      Instant instant = Instant.ofEpochSecond(timestamp);

      timestamps.add(instant.atZone(utcZoneId).toLocalDate());
    }

    return timestamps;
  }

  private List<BigDecimal> parseFundValues(JsonNode resultNode) {
    JsonNode adjCloseNode = resultNode.path("indicators").path("adjclose").get(0).path("adjclose");

    List<BigDecimal> fundValues = new ArrayList<>();

    for (Iterator<JsonNode> it = adjCloseNode.elements(); it.hasNext(); ) {
      fundValues.add(BigDecimal.valueOf(it.next().asDouble()));
    }

    return fundValues;
  }

  private String buildFetchUri(String fundName, LocalDate startDate, LocalDate endDate) {
    ZoneId utcZoneId = ZoneId.of("UTC");

    long startEpoch = startDate.atStartOfDay(utcZoneId).minusDays(1).toEpochSecond();
    long endEpoch = endDate.atStartOfDay(utcZoneId).plusDays(1).toEpochSecond();

    return UriComponentsBuilder.fromHttpUrl(
            "https://query1.finance.yahoo.com/v7/finance/chart/{ticker}.F")
        .query("interval=1d")
        .query("events=history")
        .query("includeAdjustedClose=true")
        .query("period1={startTime}")
        .query("period2={endTime}")
        .buildAndExpand(fundName, startEpoch, endEpoch)
        .toUriString();
  }
}
