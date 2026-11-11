package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import static java.math.BigDecimal.ZERO;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toSet;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException.ServiceUnavailable;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Slf4j
@Profile("!staging")
@ToString(onlyExplicitlyIncluded = true)
public class DeutscheBoerseValueRetriever implements ComparisonIndexRetriever {

  @ToString.Include public static final String KEY = "DEUTSCHE_BOERSE_VALUE";
  public static final String PROVIDER = "DEUTSCHE_BOERSE";
  private static final String XETRA_MARKET_IDENTIFIER_CODE = "XETR";
  private static final ZoneId XETRA_TIMEZONE = ZoneId.of("Europe/Berlin");
  private static final LocalTime CLOSING_PRICE_FINALIZED_TIME = LocalTime.of(6, 0);

  private final RestClient restClient;
  private final Clock clock;
  private final InstrumentReferenceService instrumentReferenceService;
  private final FundValueRepository fundValueRepository;
  private final RetryTemplate rateLimitRetryTemplate;

  public DeutscheBoerseValueRetriever(
      RestClient.Builder restClientBuilder,
      Clock clock,
      InstrumentReferenceService instrumentReferenceService,
      FundValueRepository fundValueRepository,
      @Value("${deutsche-boerse.rate-limit-retry-delay:6s}") Duration rateLimitRetryDelay) {
    this.restClient = restClientBuilder.build();
    this.clock = clock;
    this.instrumentReferenceService = instrumentReferenceService;
    this.fundValueRepository = fundValueRepository;
    this.rateLimitRetryTemplate = retryWhileRateLimited(rateLimitRetryDelay);
  }

  private static RetryTemplate retryWhileRateLimited(Duration delay) {
    return new RetryTemplate(
        RetryPolicy.builder()
            .includes(ServiceUnavailable.class)
            .maxRetries(3)
            .delay(delay)
            .multiplier(2)
            .maxDelay(delay.multipliedBy(4))
            .build());
  }

  @Override
  public String getKey() {
    return KEY;
  }

  @Override
  public Set<String> expectedStorageKeys() {
    return instrumentReferenceService.getXetraIsins().stream()
        .map(DeutscheBoerseValueRetriever::storageKey)
        .collect(toSet());
  }

  @Override
  public boolean requiresWorkingDay() {
    return true;
  }

  @Override
  public List<FundValue> retrieveValuesForRange(LocalDate startDate, LocalDate endDate) {
    LocalDate cutoff = latestFinalizedDate();
    List<String> isins = isinsNotStoredThrough(latestWeekdayOnOrBefore(cutoff));
    List<FundValue> values = new ArrayList<>();
    for (int i = 0; i < isins.size(); i++) {
      String isin = isins.get(i);
      try {
        values.addAll(retrieveValuesForIsin(isin, startDate, endDate, cutoff));
      } catch (ServiceUnavailable e) {
        log.warn(
            "Deutsche Boerse rate limit persists, deferring to next run: deferredIsins={}",
            isins.subList(i, isins.size()));
        return values;
      } catch (RuntimeException e) {
        log.error("Failed to retrieve values for ISIN: {}", isin, e);
      }
    }
    return values;
  }

  private List<String> isinsNotStoredThrough(LocalDate tradingDay) {
    List<String> isins = instrumentReferenceService.getXetraIsins();
    Map<String, LocalDate> latestDates =
        fundValueRepository.findLatestDateByKeys(
            isins.stream().map(DeutscheBoerseValueRetriever::storageKey).collect(toSet()));
    return isins.stream()
        .filter(isin -> !isStoredThrough(latestDates.get(storageKey(isin)), tradingDay))
        .toList();
  }

  private static boolean isStoredThrough(@Nullable LocalDate latestDate, LocalDate tradingDay) {
    return latestDate != null && !latestDate.isBefore(tradingDay);
  }

  private static LocalDate latestWeekdayOnOrBefore(LocalDate date) {
    if (date.getDayOfWeek() == SUNDAY) {
      return date.minusDays(2);
    }
    if (date.getDayOfWeek() == SATURDAY) {
      return date.minusDays(1);
    }
    return date;
  }

  private static String storageKey(String isin) {
    return isin + "." + XETRA_MARKET_IDENTIFIER_CODE;
  }

  private List<FundValue> retrieveValuesForIsin(
      String isin, LocalDate startDate, LocalDate endDate, LocalDate cutoff) {
    var uri = buildUri(isin, startDate, endDate);

    DeutscheBoerseResponse response =
        rateLimitRetryTemplate.invoke(
            () ->
                restClient
                    .get()
                    .uri(uri)
                    .accept(APPLICATION_JSON)
                    .retrieve()
                    .body(DeutscheBoerseResponse.class));

    if (response == null || response.data() == null) {
      return List.of();
    }

    var storageKey = storageKey(isin);
    var now = Instant.now(clock);
    List<FundValue> barValues =
        response.data().stream()
            .map(
                priceData ->
                    new FundValue(storageKey, priceData.date(), priceData.close(), PROVIDER, now))
            .toList();

    Optional<QuoteBoxResponse> quoteBox = fetchQuoteBox(isin);
    Optional<FundValue> officialLastPrice =
        quoteBox.flatMap(
            quoteBoxResponse ->
                officialLastPrice(quoteBoxResponse, storageKey, startDate, endDate, now));
    List<FundValue> allValues = withOfficialLastPrice(barValues, officialLastPrice);

    logLatestValue(storageKey, allValues);

    List<FundValue> nonZeroValues =
        allValues.stream().filter(fundValue -> fundValue.value().compareTo(ZERO) != 0).toList();

    int zeroFilteredCount = allValues.size() - nonZeroValues.size();
    if (zeroFilteredCount > 0) {
      log.warn("Filtered out {} zero-values for ISIN {} in date range", zeroFilteredCount, isin);
    }

    boolean cutoffDayConfirmed =
        officialLastPrice.isPresent()
            || quoteBox.filter(QuoteBoxResponse::isRetailSessionPrint).isPresent();

    return nonZeroValues.stream()
        .filter(fundValue -> !fundValue.date().isAfter(cutoff))
        .filter(fundValue -> cutoffDayConfirmed || !fundValue.date().equals(cutoff))
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

  private Optional<QuoteBoxResponse> fetchQuoteBox(String isin) {
    var uri =
        UriComponentsBuilder.fromUriString(
                "https://api.live.deutsche-boerse.com/v1/data/quote_box/single")
            .queryParam("isin", isin)
            .queryParam("mic", XETRA_MARKET_IDENTIFIER_CODE)
            .build()
            .toUriString();

    try {
      return Optional.ofNullable(
          rateLimitRetryTemplate.invoke(
              () ->
                  restClient
                      .get()
                      .uri(uri)
                      .accept(APPLICATION_JSON)
                      .retrieve()
                      .body(QuoteBoxResponse.class)));
    } catch (ServiceUnavailable e) {
      throw e;
    } catch (Exception e) {
      log.warn("Failed to retrieve official last price for ISIN: {}", isin, e);
      return Optional.empty();
    }
  }

  private Optional<FundValue> officialLastPrice(
      QuoteBoxResponse response,
      String storageKey,
      LocalDate startDate,
      LocalDate endDate,
      Instant now) {
    if (response.lastPrice() == null
        || response.timestampLastPrice() == null
        || response.lastPriceIndicator() != null) {
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

  private LocalDate latestFinalizedDate() {
    ZonedDateTime nowInExchangeTimezone =
        ZonedDateTime.now(clock).withZoneSameInstant(XETRA_TIMEZONE);
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

  record QuoteBoxResponse(
      BigDecimal lastPrice, Instant timestampLastPrice, String lastPriceIndicator) {

    boolean isRetailSessionPrint() {
      return "R".equals(lastPriceIndicator);
    }
  }
}
