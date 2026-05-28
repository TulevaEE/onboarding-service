package ee.tuleva.onboarding.comparisons.fundvalue;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;

import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundTicker;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PriceDataFreshnessAlertJob {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  static final int EARLIEST_ALERT_HOUR = 7;

  private final FundValueRepository fundValueRepository;
  private final OperationsNotificationService notificationService;
  private final PublicHolidays publicHolidays;
  private final Clock clock;

  private volatile LocalDate lastAlertDate;

  record ProviderKey(String provider, String storageKey) {}

  public void checkAfterIndexing() {
    ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(TALLINN);
    LocalDate today = now.toLocalDate();

    if (!publicHolidays.isWorkingDay(today)) {
      return;
    }
    if (now.getHour() < EARLIEST_ALERT_HOUR) {
      return;
    }
    if (today.equals(lastAlertDate)) {
      return;
    }

    lastAlertDate = today;
    runFreshnessCheck(today);
  }

  void runFreshnessCheck(LocalDate today) {
    LocalDate expectedDate = publicHolidays.previousWorkingDay(today);
    List<FundTicker> etfTickers = getEtfTickers();
    Map<String, ProviderKey> keyToProvider = buildKeyToProviderMap(etfTickers);

    if (keyToProvider.isEmpty()) {
      return;
    }

    Map<String, LocalDate> latestDates =
        fundValueRepository.findLatestDateByKeys(keyToProvider.keySet());

    Map<String, List<String>> staleByProvider = new LinkedHashMap<>();
    boolean anyProviderHasExpectedDate = false;

    for (var entry : keyToProvider.entrySet()) {
      String storageKey = entry.getKey();
      ProviderKey providerKey = entry.getValue();
      LocalDate latestDate = latestDates.get(storageKey);

      if (latestDate != null && !latestDate.isBefore(expectedDate)) {
        anyProviderHasExpectedDate = true;
      } else {
        staleByProvider
            .computeIfAbsent(providerKey.provider(), k -> new ArrayList<>())
            .add(storageKey);
      }
    }

    if (staleByProvider.isEmpty()) {
      return;
    }

    String message = formatAlertMessage(expectedDate, staleByProvider, anyProviderHasExpectedDate);
    log.warn("{}", message);
    notificationService.sendMessage(message, INVESTMENT);
  }

  static List<FundTicker> getEtfTickers() {
    return Arrays.stream(FundTicker.values())
        .filter(
            ticker ->
                ticker.getXetraStorageKey().isPresent()
                    || ticker.getEuronextParisStorageKey().isPresent())
        .toList();
  }

  static Map<String, ProviderKey> buildKeyToProviderMap(List<FundTicker> etfTickers) {
    Map<String, ProviderKey> map = new LinkedHashMap<>();
    for (FundTicker ticker : etfTickers) {
      ticker
          .getXetraStorageKey()
          .ifPresent(key -> map.put(key, new ProviderKey("DEUTSCHE_BOERSE", key)));
      ticker
          .getEuronextParisStorageKey()
          .ifPresent(key -> map.put(key, new ProviderKey("EURONEXT", key)));
      map.put(ticker.getEodhdTicker(), new ProviderKey("EODHD", ticker.getEodhdTicker()));
      map.put(ticker.getYahooTicker(), new ProviderKey("YAHOO", ticker.getYahooTicker()));
    }
    return map;
  }

  private String formatAlertMessage(
      LocalDate expectedDate,
      Map<String, List<String>> staleByProvider,
      boolean anyProviderHasExpectedDate) {
    StringBuilder sb = new StringBuilder();
    sb.append(
        "PRICE DATA STALE — check before NAV calculation at 11:00, expectedDate="
            + expectedDate
            + "\n");

    if (!anyProviderHasExpectedDate) {
      sb.append("  No provider has data for expectedDate — verify if exchange holiday.\n");
    }

    staleByProvider.entrySet().stream()
        .sorted(Comparator.comparingInt(e -> -e.getValue().size()))
        .forEach(
            entry -> {
              String provider = entry.getKey();
              List<String> keys = entry.getValue();
              if (keys.size() > 5) {
                String sample = keys.stream().limit(5).collect(Collectors.joining(", ")) + ", ...";
                sb.append(
                    "  "
                        + provider
                        + ": "
                        + keys.size()
                        + " instruments missing ("
                        + sample
                        + ")\n");
              } else {
                String keyList = String.join(", ", keys);
                sb.append(
                    "  "
                        + provider
                        + ": "
                        + keys.size()
                        + " instruments missing ("
                        + keyList
                        + ")\n");
              }
            });

    return sb.toString().stripTrailing();
  }
}
