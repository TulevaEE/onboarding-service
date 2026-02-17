package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import static java.math.BigDecimal.ZERO;
import static java.util.Comparator.comparing;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@Slf4j
@ToString(onlyExplicitlyIncluded = true)
public class BlackRockFundValueRetriever implements ComparisonIndexRetriever {

  @ToString.Include public static final String KEY = "BLACKROCK_VALUE";
  public static final String PROVIDER = "BLACKROCK";
  private static final ZoneId BLACKROCK_TIMEZONE = ZoneId.of("Europe/Dublin");
  private static final LocalTime CLOSING_PRICE_FINALIZED_TIME = LocalTime.of(6, 0);
  private static final Pattern NAV_DATA_PATTERN =
      Pattern.compile(
          "Date\\.UTC\\((\\d+),(\\d+),(\\d+)\\),y:Number\\(\\(([0-9.]+)\\)\\.toFixed\\(2\\)\\)");

  private final RestClient restClient;
  private final Clock clock;

  public BlackRockFundValueRetriever(RestClient.Builder restClientBuilder, Clock clock) {
    this.restClient = restClientBuilder.build();
    this.clock = clock;
  }

  @Override
  public String getKey() {
    return KEY;
  }

  @Override
  public List<FundValue> retrieveValuesForRange(LocalDate startDate, LocalDate endDate) {
    return FundTicker.getBlackrockFunds().stream()
        .flatMap(fund -> retrieveValuesForFund(fund, startDate, endDate).stream())
        .toList();
  }

  private List<FundValue> retrieveValuesForFund(
      FundTicker fund, LocalDate startDate, LocalDate endDate) {
    var url =
        "https://www.blackrock.com/se/individual/products/" + fund.getBlackrockProductId() + "/";
    var storageKey = fund.getBlackrockStorageKey().orElseThrow();

    String responseBody;
    try {
      responseBody = restClient.get().uri(url).retrieve().body(String.class);
    } catch (Exception e) {
      log.error(
          "Failed to retrieve values: fund={}, productId={}",
          fund.name(),
          fund.getBlackrockProductId(),
          e);
      return List.of();
    }

    if (responseBody == null || responseBody.isBlank()) {
      return List.of();
    }

    var now = Instant.now();
    List<FundValue> allValues = parseNavData(responseBody, storageKey, now);

    logLatestValue(storageKey, allValues);

    List<FundValue> filteredByDate =
        allValues.stream()
            .filter(
                fundValue ->
                    !fundValue.date().isBefore(startDate) && !fundValue.date().isAfter(endDate))
            .toList();

    List<FundValue> nonZeroValues =
        filteredByDate.stream()
            .filter(fundValue -> fundValue.value().compareTo(ZERO) != 0)
            .toList();

    int zeroFilteredCount = filteredByDate.size() - nonZeroValues.size();
    if (zeroFilteredCount > 0) {
      log.warn("Filtered out {} zero-values: storageKey={}", zeroFilteredCount, storageKey);
    }

    ZonedDateTime nowInTimezone = ZonedDateTime.now(clock).withZoneSameInstant(BLACKROCK_TIMEZONE);
    LocalDate cutoff = latestFinalizedDate(nowInTimezone);

    return nonZeroValues.stream().filter(fundValue -> !fundValue.date().isAfter(cutoff)).toList();
  }

  List<FundValue> parseNavData(String responseBody, String storageKey, Instant now) {
    List<FundValue> values = new ArrayList<>();
    var matcher = NAV_DATA_PATTERN.matcher(responseBody);

    while (matcher.find()) {
      int year = Integer.parseInt(matcher.group(1));
      int month = Integer.parseInt(matcher.group(2)) + 1;
      int day = Integer.parseInt(matcher.group(3));
      var value = new BigDecimal(matcher.group(4));
      var date = LocalDate.of(year, month, day);
      values.add(new FundValue(storageKey, date, value, PROVIDER, now));
    }

    return values;
  }

  private void logLatestValue(String identifier, List<FundValue> values) {
    if (values.isEmpty()) {
      log.info("BlackRock response: ticker={}, no values returned", identifier);
      return;
    }
    var latest = values.stream().max(comparing(FundValue::date)).orElseThrow();
    log.info(
        "BlackRock response: ticker={}, latestDate={}, value={}",
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
