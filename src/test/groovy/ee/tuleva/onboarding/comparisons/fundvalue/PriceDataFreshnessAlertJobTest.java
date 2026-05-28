package ee.tuleva.onboarding.comparisons.fundvalue;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundTicker;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PriceDataFreshnessAlertJobTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  // 2026-01-14 = Wednesday; UTC+2 (EET winter)
  private static final String WED_0800_UTC = "2026-01-14T06:00:00Z"; // 08:00 Tallinn
  private static final String WED_0600_UTC = "2026-01-14T04:00:00Z"; // 06:00 Tallinn
  // 2026-01-17 = Saturday
  private static final String SAT_0800_UTC = "2026-01-17T06:00:00Z";
  // 2026-01-19 = Monday
  private static final String MON_0800_UTC = "2026-01-19T06:00:00Z"; // 08:00 Tallinn
  // 2026-02-24 = Tuesday, Estonian Independence Day
  private static final String INDEPENDENCE_DAY_UTC = "2026-02-24T06:00:00Z";

  @Mock private FundValueRepository fundValueRepository;
  @Mock private OperationsNotificationService notificationService;
  private final PublicHolidays publicHolidays = new PublicHolidays();

  @Test
  void allProvidersFresh_noAlert() {
    var job = jobOn(WED_0800_UTC);
    LocalDate tuesday = LocalDate.of(2026, 1, 13);
    stubAllKeysWithDate(tuesday);

    job.checkAfterIndexing();

    verifyNoInteractions(notificationService);
  }

  @Test
  void oneProviderStale_alertsWithProviderAndInstruments() {
    var job = jobOn(WED_0800_UTC);
    LocalDate tuesday = LocalDate.of(2026, 1, 13);
    LocalDate friday = LocalDate.of(2026, 1, 9);

    Map<String, LocalDate> latestDates = buildAllFreshDates(tuesday);
    makeEodhdStale(latestDates, friday);
    when(fundValueRepository.findLatestDateByKeys(any())).thenReturn(latestDates);

    job.checkAfterIndexing();

    verify(notificationService)
        .sendMessage(
            argThat((String msg) -> msg.contains("EODHD") && msg.contains("PRICE DATA STALE")),
            eq(INVESTMENT));
  }

  @Test
  void providerKeyMissingFromResults_alertsForMissingKey() {
    var job = jobOn(WED_0800_UTC);
    LocalDate tuesday = LocalDate.of(2026, 1, 13);

    Map<String, LocalDate> latestDates = buildAllFreshDates(tuesday);
    String firstXetraKey = FundTicker.values()[0].getXetraStorageKey().orElse("IE00BFNM3G45.XETR");
    latestDates.remove(firstXetraKey);
    when(fundValueRepository.findLatestDateByKeys(any())).thenReturn(latestDates);

    job.checkAfterIndexing();

    verify(notificationService)
        .sendMessage(
            argThat((String msg) -> msg.contains("DEUTSCHE_BOERSE") && msg.contains(firstXetraKey)),
            eq(INVESTMENT));
  }

  @Test
  void allProvidersMissing_alertsWithExchangeHolidayHint() {
    var job = jobOn(WED_0800_UTC);
    LocalDate monday = LocalDate.of(2026, 1, 12);
    stubAllKeysWithDate(monday);

    job.checkAfterIndexing();

    verify(notificationService)
        .sendMessage(
            argThat(
                (String msg) ->
                    msg.contains("PRICE DATA STALE") && msg.contains("verify if exchange holiday")),
            eq(INVESTMENT));
  }

  @Test
  void partialOutage_alertsOnlyForStaleProvider() {
    var job = jobOn(WED_0800_UTC);
    LocalDate tuesday = LocalDate.of(2026, 1, 13);
    LocalDate monday = LocalDate.of(2026, 1, 12);

    Map<String, LocalDate> latestDates = buildAllFreshDates(tuesday);
    makeEodhdStale(latestDates, monday);
    when(fundValueRepository.findLatestDateByKeys(any())).thenReturn(latestDates);

    job.checkAfterIndexing();

    verify(notificationService)
        .sendMessage(
            argThat(
                (String msg) ->
                    msg.contains("EODHD") && !msg.contains("verify if exchange holiday")),
            eq(INVESTMENT));
  }

  @Test
  void silentOnWeekend() {
    var job = jobOn(SAT_0800_UTC);

    job.checkAfterIndexing();

    verifyNoInteractions(notificationService);
    verifyNoInteractions(fundValueRepository);
  }

  @Test
  void silentOnEstonianPublicHoliday() {
    var job = jobOn(INDEPENDENCE_DAY_UTC);

    job.checkAfterIndexing();

    verifyNoInteractions(notificationService);
    verifyNoInteractions(fundValueRepository);
  }

  @Test
  void silentBeforeEarliestAlertHour() {
    var job = jobOn(WED_0600_UTC);

    job.checkAfterIndexing();

    verifyNoInteractions(notificationService);
    verifyNoInteractions(fundValueRepository);
  }

  @Test
  void mondayExpectsFriday_allFresh_noAlert() {
    var job = jobOn(MON_0800_UTC);
    LocalDate friday = LocalDate.of(2026, 1, 16);
    stubAllKeysWithDate(friday);

    job.checkAfterIndexing();

    verifyNoInteractions(notificationService);
  }

  @Test
  void multipleStaleProviders_singleMessage() {
    var job = jobOn(WED_0800_UTC);
    LocalDate tuesday = LocalDate.of(2026, 1, 13);
    LocalDate friday = LocalDate.of(2026, 1, 9);

    Map<String, LocalDate> latestDates = buildAllFreshDates(tuesday);
    makeEodhdStale(latestDates, friday);
    makeYahooStale(latestDates, friday);
    when(fundValueRepository.findLatestDateByKeys(any())).thenReturn(latestDates);

    job.checkAfterIndexing();

    verify(notificationService)
        .sendMessage(
            argThat((String msg) -> msg.contains("EODHD") && msg.contains("YAHOO")),
            eq(INVESTMENT));
  }

  @Test
  void alertsOncePerDay_secondCallSilent() {
    var job = jobOn(WED_0800_UTC);
    LocalDate monday = LocalDate.of(2026, 1, 12);
    stubAllKeysWithDate(monday);

    job.checkAfterIndexing();
    verify(notificationService).sendMessage(any(), eq(INVESTMENT));

    job.checkAfterIndexing();
    // still only 1 invocation total
    verify(notificationService).sendMessage(any(), eq(INVESTMENT));
  }

  @Test
  void getEtfTickers_excludesMutualFunds() {
    List<FundTicker> etfTickers = PriceDataFreshnessAlertJob.getEtfTickers();

    for (FundTicker ticker : etfTickers) {
      assertThat(ticker.getEodhdTicker()).doesNotEndWith(".EUFUND");
      assertThat(
              ticker.getXetraStorageKey().isPresent()
                  || ticker.getEuronextParisStorageKey().isPresent())
          .isTrue();
    }
    assertThat(etfTickers.size()).isGreaterThan(0);
  }

  @Test
  void buildKeyToProviderMap_containsAllExpectedProviders() {
    List<FundTicker> etfTickers = PriceDataFreshnessAlertJob.getEtfTickers();
    Map<String, PriceDataFreshnessAlertJob.ProviderKey> map =
        PriceDataFreshnessAlertJob.buildKeyToProviderMap(etfTickers);

    Set<String> providers = new HashSet<>();
    map.values().forEach(pk -> providers.add(pk.provider()));

    assertThat(providers)
        .containsExactlyInAnyOrder("DEUTSCHE_BOERSE", "EURONEXT", "EODHD", "YAHOO");
  }

  private void stubAllKeysWithDate(LocalDate date) {
    when(fundValueRepository.findLatestDateByKeys(any())).thenReturn(buildAllFreshDates(date));
  }

  private Map<String, LocalDate> buildAllFreshDates(LocalDate date) {
    List<FundTicker> etfTickers = PriceDataFreshnessAlertJob.getEtfTickers();
    Map<String, PriceDataFreshnessAlertJob.ProviderKey> keyMap =
        PriceDataFreshnessAlertJob.buildKeyToProviderMap(etfTickers);
    Map<String, LocalDate> result = new HashMap<>();
    keyMap.keySet().forEach(key -> result.put(key, date));
    return result;
  }

  private void makeEodhdStale(Map<String, LocalDate> latestDates, LocalDate staleDate) {
    List<FundTicker> etfTickers = PriceDataFreshnessAlertJob.getEtfTickers();
    for (FundTicker ticker : etfTickers) {
      latestDates.put(ticker.getEodhdTicker(), staleDate);
    }
  }

  private void makeYahooStale(Map<String, LocalDate> latestDates, LocalDate staleDate) {
    List<FundTicker> etfTickers = PriceDataFreshnessAlertJob.getEtfTickers();
    for (FundTicker ticker : etfTickers) {
      latestDates.put(ticker.getYahooTicker(), staleDate);
    }
  }

  private PriceDataFreshnessAlertJob jobOn(String instant) {
    Clock clock = Clock.fixed(Instant.parse(instant), TALLINN);
    return new PriceDataFreshnessAlertJob(
        fundValueRepository, notificationService, publicHolidays, clock);
  }
}
