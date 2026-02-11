package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import static java.math.BigDecimal.ZERO;
import static java.util.Comparator.comparing;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import java.io.BufferedReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Slf4j
@ToString(onlyExplicitlyIncluded = true)
public class EuronextValueRetriever implements ComparisonIndexRetriever {

  @ToString.Include public static final String KEY = "EURONEXT_VALUE";
  public static final String PROVIDER = "EURONEXT";
  private static final String EURONEXT_PARIS_MARKET_IDENTIFIER_CODE = "XPAR";
  private static final int HEADER_LINES_TO_SKIP = 4;
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final ZoneId EURONEXT_TIMEZONE = ZoneId.of("Europe/Paris");
  private static final LocalTime CLOSING_PRICE_FINALIZED_TIME = LocalTime.of(6, 0);

  private final RestClient restClient;
  private final Clock clock;

  public EuronextValueRetriever(RestClient.Builder restClientBuilder, Clock clock) {
    this.restClient = restClientBuilder.build();
    this.clock = clock;
  }

  @Override
  public String getKey() {
    return KEY;
  }

  @Override
  public List<FundValue> retrieveValuesForRange(LocalDate startDate, LocalDate endDate) {
    return FundTicker.getEuronextParisIsins().stream()
        .flatMap(isin -> retrieveValuesForIsin(isin, startDate, endDate).stream())
        .toList();
  }

  private List<FundValue> retrieveValuesForIsin(
      String isin, LocalDate startDate, LocalDate endDate) {
    var uri = buildUri(isin, startDate, endDate);

    String csvResponse;
    try {
      csvResponse = restClient.get().uri(uri).retrieve().body(String.class);
    } catch (Exception e) {
      log.error("Failed to retrieve values for ISIN: {}", isin, e);
      return List.of();
    }

    if (csvResponse == null || csvResponse.isBlank()) {
      return List.of();
    }

    var storageKey = isin + "." + EURONEXT_PARIS_MARKET_IDENTIFIER_CODE;
    var now = Instant.now();
    List<FundValue> allValues = parseCsvResponse(csvResponse, storageKey, now);

    logLatestValue(storageKey, allValues);

    List<FundValue> nonZeroValues =
        allValues.stream().filter(fundValue -> fundValue.value().compareTo(ZERO) != 0).toList();

    int zeroFilteredCount = allValues.size() - nonZeroValues.size();
    if (zeroFilteredCount > 0) {
      log.warn("Filtered out {} zero-values for ISIN {} in date range", zeroFilteredCount, isin);
    }

    ZonedDateTime nowInCET = ZonedDateTime.now(clock).withZoneSameInstant(EURONEXT_TIMEZONE);
    LocalDate cutoff = latestFinalizedDate(nowInCET);

    return nonZeroValues.stream().filter(fundValue -> !fundValue.date().isAfter(cutoff)).toList();
  }

  private String buildUri(String isin, LocalDate startDate, LocalDate endDate) {
    return UriComponentsBuilder.fromUriString(
            "https://live.euronext.com/en/ajax/AwlHistoricalPrice/getFullDownloadAjax/"
                + isin
                + "-"
                + EURONEXT_PARIS_MARKET_IDENTIFIER_CODE)
        .queryParam("format", "csv")
        .queryParam("decimal_separator", ".")
        .queryParam("date_form", "d/m/Y")
        .queryParam("adjusted", "Y")
        .queryParam("startdate", startDate)
        .queryParam("enddate", endDate)
        .build()
        .toUriString();
  }

  private List<FundValue> parseCsvResponse(String csvResponse, String storageKey, Instant now) {
    List<FundValue> values = new ArrayList<>();

    try (var reader = new BufferedReader(new StringReader(csvResponse))) {
      for (int i = 0; i < HEADER_LINES_TO_SKIP; i++) {
        reader.readLine();
      }

      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isBlank()) {
          continue;
        }

        String[] parts = line.split(";");
        if (parts.length < 6) {
          continue;
        }

        var date = LocalDate.parse(parts[0], DATE_FORMATTER);
        var closePrice = new BigDecimal(parts[5]);

        values.add(new FundValue(storageKey, date, closePrice, PROVIDER, now));
      }
    } catch (Exception e) {
      log.error("Failed to parse CSV response for key: {}", storageKey, e);
      return List.of();
    }

    return values;
  }

  private void logLatestValue(String identifier, List<FundValue> values) {
    if (values.isEmpty()) {
      log.info("Euronext API response: ticker={}, no values returned", identifier);
      return;
    }
    var latest = values.stream().max(comparing(FundValue::date)).orElseThrow();
    log.info(
        "Euronext API response: ticker={}, latestDate={}, value={}",
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
}
