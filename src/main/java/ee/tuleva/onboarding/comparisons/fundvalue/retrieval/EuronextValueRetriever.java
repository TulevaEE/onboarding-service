package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import static java.math.BigDecimal.ZERO;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toSet;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
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
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final ZoneId EURONEXT_TIMEZONE = ZoneId.of("Europe/Paris");
  private static final LocalTime CLOSING_PRICE_FINALIZED_TIME = LocalTime.of(6, 0);

  private final RestClient restClient;
  private final Clock clock;
  private final InstrumentReferenceService instrumentReferenceService;

  public EuronextValueRetriever(
      RestClient.Builder restClientBuilder,
      Clock clock,
      InstrumentReferenceService instrumentReferenceService) {
    this.restClient = restClientBuilder.build();
    this.clock = clock;
    this.instrumentReferenceService = instrumentReferenceService;
  }

  @Override
  public String getKey() {
    return KEY;
  }

  @Override
  public Set<String> expectedStorageKeys() {
    return instrumentReferenceService.getEuronextParisIsins().stream()
        .map(isin -> isin + "." + EURONEXT_PARIS_MARKET_IDENTIFIER_CODE)
        .collect(toSet());
  }

  @Override
  public boolean requiresWorkingDay() {
    return true;
  }

  @Override
  public List<FundValue> retrieveValuesForRange(LocalDate startDate, LocalDate endDate) {
    return instrumentReferenceService.getEuronextParisIsins().stream()
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
    var now = clock.instant();
    List<FundValue> allValues = parseCsvResponse(isin, csvResponse, storageKey, now);

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

  private List<FundValue> parseCsvResponse(
      String isin, String csvResponse, String storageKey, Instant now) {
    List<List<String>> lines = csvResponse.lines().map(EuronextValueRetriever::columns).toList();
    OptionalInt headerLine = headerLine(lines);
    if (headerLine.isEmpty()) {
      log.error("Euronext CSV has no header row: storageKey={}", storageKey);
      return List.of();
    }
    int headerIndex = headerLine.getAsInt();

    List<String> preamble = lines.subList(0, headerIndex).stream().map(List::getFirst).toList();
    if (!preamble.contains(isin)) {
      log.error(
          "Euronext CSV is not for the requested instrument: storageKey={}, preamble={}",
          storageKey,
          preamble);
      return List.of();
    }

    List<String> header = lines.get(headerIndex);
    int closeColumn = header.indexOf("Close");
    if (closeColumn < 0) {
      log.error(
          "Euronext CSV has no official closing price column: storageKey={}, header={}",
          storageKey,
          header);
      return List.of();
    }

    return parseRows(lines.subList(headerIndex + 1, lines.size()), closeColumn, storageKey, now);
  }

  private OptionalInt headerLine(List<List<String>> lines) {
    return IntStream.range(0, lines.size())
        .filter(index -> lines.get(index).getFirst().equals("Date"))
        .findFirst();
  }

  private List<FundValue> parseRows(
      List<List<String>> rows, int closeColumn, String storageKey, Instant now) {
    try {
      return rows.stream()
          .filter(row -> row.size() > closeColumn && !row.get(closeColumn).isEmpty())
          .map(
              row ->
                  new FundValue(
                      storageKey,
                      LocalDate.parse(row.getFirst(), DATE_FORMATTER),
                      new BigDecimal(row.get(closeColumn)),
                      PROVIDER,
                      now))
          .toList();
    } catch (Exception e) {
      log.error("Failed to parse Euronext CSV rows: storageKey={}", storageKey, e);
      return List.of();
    }
  }

  private static List<String> columns(String line) {
    return Stream.of(line.split(";", -1)).map(column -> column.replace("\"", "").strip()).toList();
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
