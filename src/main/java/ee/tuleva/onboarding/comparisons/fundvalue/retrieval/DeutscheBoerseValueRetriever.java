package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import static java.math.BigDecimal.ZERO;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toSet;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Slf4j
@ToString(onlyExplicitlyIncluded = true)
public class DeutscheBoerseValueRetriever implements ComparisonIndexRetriever {

  @ToString.Include public static final String KEY = "DEUTSCHE_BOERSE_VALUE";
  public static final String PROVIDER = "DEUTSCHE_BOERSE";
  private static final String XETRA_MARKET_IDENTIFIER_CODE = "XETR";
  private static final ZoneId XETRA_TIMEZONE = ZoneId.of("Europe/Berlin");
  private static final LocalTime CLOSING_PRICE_FINALIZED_TIME = LocalTime.of(6, 0);

  private final RestClient restClient;
  private final Clock clock;

  public DeutscheBoerseValueRetriever(RestClient.Builder restClientBuilder, Clock clock) {
    this.restClient = restClientBuilder.build();
    this.clock = clock;
  }

  @Override
  public String getKey() {
    return KEY;
  }

  @Override
  public Set<String> expectedStorageKeys() {
    return FundTicker.getXetraIsins().stream()
        .map(isin -> isin + "." + XETRA_MARKET_IDENTIFIER_CODE)
        .collect(toSet());
  }

  @Override
  public boolean requiresWorkingDay() {
    return true;
  }

  @Override
  public List<FundValue> retrieveValuesForRange(LocalDate startDate, LocalDate endDate) {
    return FundTicker.getXetraIsins().stream()
        .flatMap(isin -> retrieveValuesForIsin(isin, startDate, endDate).stream())
        .toList();
  }

  private List<FundValue> retrieveValuesForIsin(
      String isin, LocalDate startDate, LocalDate endDate) {
    var uri = buildUri(isin, startDate, endDate);

    DeutscheBoerseResponse response;
    try {
      response =
          restClient
              .get()
              .uri(uri)
              .accept(APPLICATION_JSON)
              .retrieve()
              .body(DeutscheBoerseResponse.class);
    } catch (Exception e) {
      log.error("Failed to retrieve values for ISIN: {}", isin, e);
      return List.of();
    }

    if (response == null || response.data() == null) {
      return List.of();
    }

    var storageKey = isin + "." + XETRA_MARKET_IDENTIFIER_CODE;
    var now = Instant.now(clock);
    List<FundValue> barValues =
        response.data().stream()
            .map(
                priceData ->
                    new FundValue(storageKey, priceData.date(), priceData.close(), PROVIDER, now))
            .toList();

    Optional<FundValue> officialLastPrice =
        fetchOfficialLastPrice(isin, storageKey, startDate, endDate, now);
    List<FundValue> allValues = withOfficialLastPrice(barValues, officialLastPrice);

    logLatestValue(storageKey, allValues);

    List<FundValue> nonZeroValues =
        allValues.stream().filter(fundValue -> fundValue.value().compareTo(ZERO) != 0).toList();

    int zeroFilteredCount = allValues.size() - nonZeroValues.size();
    if (zeroFilteredCount > 0) {
      log.warn("Filtered out {} zero-values for ISIN {} in date range", zeroFilteredCount, isin);
    }

    ZonedDateTime nowInCET = ZonedDateTime.now(clock).withZoneSameInstant(XETRA_TIMEZONE);
    LocalDate cutoff = latestFinalizedDate(nowInCET);

    return nonZeroValues.stream()
        .filter(fundValue -> !fundValue.date().isAfter(cutoff))
        .filter(fundValue -> officialLastPrice.isPresent() || !fundValue.date().equals(cutoff))
        .toList();
  }

  private List<FundValue> withOfficialLastPrice(
      List<FundValue> barValues, Optional<FundValue> officialLastPrice) {
    return officialLastPrice
        .map(
            official ->
                Stream.concat(
                        barValues.stream()
                            .filter(barValue -> !barValue.date().equals(official.date())),
                        Stream.of(official))
                    .toList())
        .orElse(barValues);
  }

  private Optional<FundValue> fetchOfficialLastPrice(
      String isin, String storageKey, LocalDate startDate, LocalDate endDate, Instant now) {
    var uri =
        UriComponentsBuilder.fromUriString(
                "https://api.live.deutsche-boerse.com/v1/data/quote_box/single")
            .queryParam("isin", isin)
            .queryParam("mic", XETRA_MARKET_IDENTIFIER_CODE)
            .build()
            .toUriString();

    QuoteBoxResponse response;
    try {
      response =
          restClient
              .get()
              .uri(uri)
              .accept(APPLICATION_JSON)
              .retrieve()
              .body(QuoteBoxResponse.class);
    } catch (Exception e) {
      log.warn("Failed to retrieve official last price for ISIN: {}", isin, e);
      return Optional.empty();
    }

    if (response == null || response.lastPrice() == null || response.timestampLastPrice() == null) {
      return Optional.empty();
    }

    LocalDate lastPriceDate = response.timestampLastPrice().atZone(XETRA_TIMEZONE).toLocalDate();
    if (lastPriceDate.isBefore(startDate) || lastPriceDate.isAfter(endDate)) {
      return Optional.empty();
    }
    return Optional.of(
        new FundValue(storageKey, lastPriceDate, response.lastPrice(), PROVIDER, now));
  }

  private void logLatestValue(String identifier, List<FundValue> values) {
    if (values.isEmpty()) {
      log.info("Deutsche Boerse API response: ticker={}, no values returned", identifier);
      return;
    }
    var latest = values.stream().max(comparing(FundValue::date)).orElseThrow();
    log.info(
        "Deutsche Boerse API response: ticker={}, latestDate={}, value={}",
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

  private String buildUri(String isin, LocalDate startDate, LocalDate endDate) {
    return UriComponentsBuilder.fromUriString(
            "https://mobile-api.live.deutsche-boerse.com/v1/data/price_history")
        .queryParam("isin", isin)
        .queryParam("mic", XETRA_MARKET_IDENTIFIER_CODE)
        .queryParam("minDate", startDate)
        .queryParam("maxDate", endDate)
        .build()
        .toUriString();
  }

  record DeutscheBoerseResponse(String isin, List<PriceData> data, Integer totalCount) {}

  record PriceData(
      LocalDate date,
      BigDecimal open,
      BigDecimal close,
      BigDecimal high,
      BigDecimal low,
      Long turnoverPieces,
      BigDecimal turnoverEuro) {}

  record QuoteBoxResponse(BigDecimal lastPrice, Instant timestampLastPrice) {}
}
