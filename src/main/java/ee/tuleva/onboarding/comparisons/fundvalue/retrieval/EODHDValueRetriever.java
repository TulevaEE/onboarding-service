package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import static java.math.BigDecimal.ZERO;
import static java.util.Comparator.comparing;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.fasterxml.jackson.annotation.JsonProperty;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Slf4j
@ToString(onlyExplicitlyIncluded = true)
public class EODHDValueRetriever implements ComparisonIndexRetriever {

  @ToString.Include public static final String KEY = "EODHD_VALUE";
  public static final String PROVIDER = "EODHD";
  private static final ZoneId EUROPE_BERLIN = ZoneId.of("Europe/Berlin");
  private static final LocalTime CLOSING_PRICE_FINALIZED_TIME = LocalTime.of(6, 0);

  private final RestClient restClient;
  private final Clock clock;
  private final String apiToken;

  public EODHDValueRetriever(
      RestClient.Builder restClientBuilder,
      Clock clock,
      @Value("${eodhd.api-token:}") String apiToken) {
    this.restClient = restClientBuilder.build();
    this.clock = clock;
    this.apiToken = apiToken;
  }

  @Override
  public String getKey() {
    return KEY;
  }

  @Override
  public List<FundValue> retrieveValuesForRange(LocalDate startDate, LocalDate endDate) {
    return FundTicker.getEodhdTickers().stream()
        .flatMap(ticker -> retrieveValuesForTicker(ticker, startDate, endDate).stream())
        .toList();
  }

  private List<FundValue> retrieveValuesForTicker(
      String ticker, LocalDate startDate, LocalDate endDate) {
    var apiTicker = stripProviderSuffix(ticker);
    var uri = buildUri(apiTicker, startDate, endDate);

    EODHDResponse[] response;
    try {
      response =
          restClient.get().uri(uri).accept(APPLICATION_JSON).retrieve().body(EODHDResponse[].class);
    } catch (Exception e) {
      log.error("Failed to retrieve values for ticker: {}", ticker, e);
      return List.of();
    }

    if (response == null) {
      return List.of();
    }

    var now = Instant.now();
    List<FundValue> allValues =
        Arrays.stream(response)
            .map(
                eodhdResponse ->
                    new FundValue(
                        ticker, eodhdResponse.date(), eodhdResponse.adjustedClose(), PROVIDER, now))
            .toList();

    logLatestValue(ticker, allValues);

    List<FundValue> nonZeroValues =
        allValues.stream().filter(fundValue -> fundValue.value().compareTo(ZERO) != 0).toList();

    int filteredCount = allValues.size() - nonZeroValues.size();
    if (filteredCount > 0) {
      log.warn("Filtered out {} zero-values for ticker {} in date range", filteredCount, ticker);
    }

    ZonedDateTime nowInCET = ZonedDateTime.now(clock).withZoneSameInstant(EUROPE_BERLIN);
    LocalDate cutoff = latestFinalizedDate(nowInCET);

    return nonZeroValues.stream().filter(fundValue -> !fundValue.date().isAfter(cutoff)).toList();
  }

  private String stripProviderSuffix(String ticker) {
    return ticker.endsWith("." + PROVIDER) ? ticker.substring(0, ticker.lastIndexOf(".")) : ticker;
  }

  private String buildUri(String ticker, LocalDate startDate, LocalDate endDate) {
    return UriComponentsBuilder.fromUriString("https://eodhd.com/api/eod/{ticker}")
        .queryParam("api_token", apiToken)
        .queryParam("fmt", "json")
        .queryParam("from", startDate)
        .queryParam("to", endDate)
        .buildAndExpand(ticker)
        .toUriString();
  }

  private void logLatestValue(String identifier, List<FundValue> values) {
    if (values.isEmpty()) {
      log.info("EODHD API response: ticker={}, no values returned", identifier);
      return;
    }
    var latest = values.stream().max(comparing(FundValue::date)).orElseThrow();
    log.info(
        "EODHD API response: ticker={}, latestDate={}, value={}",
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

  record EODHDResponse(
      LocalDate date,
      BigDecimal open,
      BigDecimal high,
      BigDecimal low,
      BigDecimal close,
      @JsonProperty("adjusted_close") BigDecimal adjustedClose,
      Long volume) {}
}
