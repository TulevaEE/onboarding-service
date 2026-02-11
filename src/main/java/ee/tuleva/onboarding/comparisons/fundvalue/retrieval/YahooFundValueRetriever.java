package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import static java.math.BigDecimal.ZERO;
import static java.time.ZoneOffset.UTC;
import static java.util.Comparator.comparing;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.YahooFundValueRetriever.YahooFinanceResponse.Result;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
public class YahooFundValueRetriever implements ComparisonIndexRetriever {
  @ToString.Include public static final String KEY = "NAV_CHECK_VALUE";
  public static final String PROVIDER = "YAHOO";

  public static final List<String> FUND_TICKERS = FundTicker.getYahooTickers();

  private static final ZoneId EUROPE_BERLIN = ZoneId.of("Europe/Berlin");
  private static final LocalTime CLOSING_PRICE_FINALIZED_TIME = LocalTime.of(6, 0);

  private final RestClient restClient;
  private final Clock clock;

  public YahooFundValueRetriever(RestClient.Builder restClientBuilder, Clock clock) {
    this.restClient = restClientBuilder.build();
    this.clock = clock;
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

    YahooFinanceResponse response =
        restClient
            .get()
            .uri(fetchUri)
            .accept(APPLICATION_JSON)
            .retrieve()
            .body(YahooFinanceResponse.class);

    Result result = response.chart().result().getFirst();
    List<LocalDate> timestamps = parseTimestamps(result.timestamp());
    List<BigDecimal> fundValues = result.indicators().adjclose().getFirst().adjclose();

    if (fundValues.size() != timestamps.size()) {
      throw new IllegalStateException(
          "Yahoo Finance response timestamp and fund values count do not match: fund=" + fundName);
    }

    var now = Instant.now();
    List<FundValue> allValues =
        IntStream.range(0, fundValues.size())
            .mapToObj(
                i -> new FundValue(fundName, timestamps.get(i), fundValues.get(i), PROVIDER, now))
            .toList();

    logLatestValue(fundName, allValues);

    List<FundValue> nonZeroValues =
        allValues.stream()
            .filter(
                fundValue -> fundValue.value() != null && fundValue.value().compareTo(ZERO) != 0)
            .toList();

    int filteredCount = allValues.size() - nonZeroValues.size();
    if (filteredCount > 0) {
      log.warn("Filtered out {} zero-values for fund {} in date range", filteredCount, fundName);
    }

    ZonedDateTime nowInCET = ZonedDateTime.now(clock).withZoneSameInstant(EUROPE_BERLIN);
    LocalDate cutoff = latestFinalizedDate(nowInCET);

    return nonZeroValues.stream().filter(fundValue -> !fundValue.date().isAfter(cutoff)).toList();
  }

  private String buildFetchUri(String fundName, LocalDate startDate, LocalDate endDate) {
    long startEpoch = startDate.atStartOfDay(UTC).minusDays(1).toEpochSecond();
    long endEpoch = endDate.atStartOfDay(UTC).plusDays(1).toEpochSecond();

    return UriComponentsBuilder.fromUriString(
            "https://query1.finance.yahoo.com/v8/finance/chart/{ticker}")
        .query("interval=1d")
        .query("events=history")
        .query("includeAdjustedClose=true")
        .query("period1={startTime}")
        .query("period2={endTime}")
        .buildAndExpand(fundName, startEpoch, endEpoch)
        .toUriString();
  }

  private List<LocalDate> parseTimestamps(List<Long> epochTimestamps) {
    return epochTimestamps.stream()
        .map(timestamp -> Instant.ofEpochSecond(timestamp).atZone(UTC).toLocalDate())
        .toList();
  }

  private void logLatestValue(String identifier, List<FundValue> values) {
    if (values.isEmpty()) {
      log.info("Yahoo API response: ticker={}, no values returned", identifier);
      return;
    }
    var latest = values.stream().max(comparing(FundValue::date)).orElseThrow();
    log.info(
        "Yahoo API response: ticker={}, latestDate={}, value={}",
        identifier,
        latest.date(),
        latest.value());
  }

  private LocalDate latestFinalizedDate(ZonedDateTime nowInExchangeTimezone) {
    if (nowInExchangeTimezone.toLocalTime().isBefore(CLOSING_PRICE_FINALIZED_TIME)) {
      return nowInExchangeTimezone.toLocalDate().minusDays(2);
    }
    return nowInExchangeTimezone.toLocalDate().minusDays(1);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record YahooFinanceResponse(Chart chart) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Chart(List<Result> result) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Result(List<Long> timestamp, Indicators indicators) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Indicators(List<AdjClose> adjclose) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdjClose(List<BigDecimal> adjclose) {}
  }
}
