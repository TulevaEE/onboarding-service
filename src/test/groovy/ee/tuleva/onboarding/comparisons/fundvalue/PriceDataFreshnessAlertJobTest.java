package ee.tuleva.onboarding.comparisons.fundvalue;

import static ee.tuleva.onboarding.instrument.InstrumentReferenceFixture.instrument;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.instrument.InstrumentReference;
import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
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

  private static final InstrumentReference XETRA_ETF =
      instrument("IE00BFNM3G45").eodhdTicker("SGAS.XETRA").yahooTicker("SGAS.DE").build();
  private static final InstrumentReference PARIS_ETF =
      instrument("LU1708330318").eodhdTicker("GAGH.PA.EODHD").yahooTicker("GAGH.PA").build();
  private static final InstrumentReference MUTUAL_FUND =
      instrument("IE00BFG1TM61")
          .eodhdTicker("IE00BFG1TM61.EUFUND")
          .yahooTicker("0P000152G5.F")
          .build();

  private static final List<InstrumentReference> ACTIVE_INSTRUMENTS =
      List.of(XETRA_ETF, PARIS_ETF, MUTUAL_FUND);

  @Mock private FundValueRepository fundValueRepository;
  @Mock private OperationsNotificationService notificationService;
  @Mock private InstrumentReferenceService instrumentReferenceService;
  private final PublicHolidays publicHolidays = new PublicHolidays();

  @BeforeEach
  void setUpInstruments() {
    lenient().when(instrumentReferenceService.activeInstruments()).thenReturn(ACTIVE_INSTRUMENTS);
  }

  @Test
  void allProvidersFresh_noAlert() {
    var job = jobOn(WED_0800_UTC);
    LocalDate tuesday = LocalDate.of(2026, 1, 13);
    stubAllKeysWithDate(job, tuesday);

    job.checkAfterIndexing();

    verifyNoInteractions(notificationService);
  }

  @Test
  void oneProviderStale_alertsWithProviderAndInstruments() {
    var job = jobOn(WED_0800_UTC);
    LocalDate tuesday = LocalDate.of(2026, 1, 13);
    LocalDate friday = LocalDate.of(2026, 1, 9);

    Map<String, LocalDate> latestDates = buildAllFreshDates(job, tuesday);
    makeEodhdStale(job, latestDates, friday);
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

    Map<String, LocalDate> latestDates = buildAllFreshDates(job, tuesday);
    String firstXetraKey = XETRA_ETF.getXetraStorageKey().orElseThrow();
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
    stubAllKeysWithDate(job, monday);

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

    Map<String, LocalDate> latestDates = buildAllFreshDates(job, tuesday);
    makeEodhdStale(job, latestDates, monday);
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
    stubAllKeysWithDate(job, friday);

    job.checkAfterIndexing();

    verifyNoInteractions(notificationService);
  }

  @Test
  void multipleStaleProviders_singleMessage() {
    var job = jobOn(WED_0800_UTC);
    LocalDate tuesday = LocalDate.of(2026, 1, 13);
    LocalDate friday = LocalDate.of(2026, 1, 9);

    Map<String, LocalDate> latestDates = buildAllFreshDates(job, tuesday);
    makeEodhdStale(job, latestDates, friday);
    makeXetraStale(job, latestDates, friday);
    when(fundValueRepository.findLatestDateByKeys(any())).thenReturn(latestDates);

    job.checkAfterIndexing();

    verify(notificationService)
        .sendMessage(
            argThat((String msg) -> msg.contains("EODHD") && msg.contains("DEUTSCHE_BOERSE")),
            eq(INVESTMENT));
  }

  @Test
  void alertsOncePerDay_secondCallSilent() {
    var job = jobOn(WED_0800_UTC);
    LocalDate monday = LocalDate.of(2026, 1, 12);
    stubAllKeysWithDate(job, monday);

    job.checkAfterIndexing();
    verify(notificationService).sendMessage(any(), eq(INVESTMENT));

    job.checkAfterIndexing();
    // still only 1 invocation total
    verify(notificationService).sendMessage(any(), eq(INVESTMENT));
  }

  @Test
  void staleAppearingLaterInDay_alertsOnLaterRun() {
    var job = jobOn(WED_0800_UTC);
    LocalDate tuesday = LocalDate.of(2026, 1, 13);
    LocalDate friday = LocalDate.of(2026, 1, 9);
    Map<String, LocalDate> stale = buildAllFreshDates(job, tuesday);
    makeEodhdStale(job, stale, friday);
    Map<String, LocalDate> fresh = buildAllFreshDates(job, tuesday);
    when(fundValueRepository.findLatestDateByKeys(any())).thenReturn(fresh).thenReturn(stale);

    job.checkAfterIndexing();
    verifyNoInteractions(notificationService);

    job.checkAfterIndexing();
    verify(notificationService).sendMessage(any(), eq(INVESTMENT));
  }

  @Test
  void retriesAfterFailure() {
    var job = jobOn(WED_0800_UTC);
    Map<String, LocalDate> freshDates = buildAllFreshDates(job, LocalDate.of(2026, 1, 12));
    when(fundValueRepository.findLatestDateByKeys(any()))
        .thenThrow(new RuntimeException("DB down"))
        .thenReturn(freshDates);

    try {
      job.checkAfterIndexing();
    } catch (Exception ignored) {
    }

    job.checkAfterIndexing();

    verify(fundValueRepository, org.mockito.Mockito.times(2)).findLatestDateByKeys(any());
  }

  @Test
  void getEtfInstruments_excludesMutualFunds() {
    var job = jobOn(WED_0800_UTC);

    List<InstrumentReference> etfInstruments = job.getEtfInstruments();

    for (InstrumentReference etfInstrument : etfInstruments) {
      assertThat(etfInstrument.getEodhdTicker()).doesNotEndWith(".EUFUND");
      assertThat(
              etfInstrument.getXetraStorageKey().isPresent()
                  || etfInstrument.getEuronextParisStorageKey().isPresent())
          .isTrue();
    }
    assertThat(etfInstruments.size()).isGreaterThan(0);
  }

  @Test
  void getEtfInstruments_readsOnlyTheActiveUniverse() {
    var job = jobOn(WED_0800_UTC);

    assertThat(job.getEtfInstruments()).containsExactly(XETRA_ETF, PARIS_ETF);
    verify(instrumentReferenceService).activeInstruments();
  }

  @Test
  void buildKeyToProviderMap_containsAllExpectedProviders() {
    var job = jobOn(WED_0800_UTC);

    Map<String, PriceDataFreshnessAlertJob.ProviderKey> map =
        job.buildKeyToProviderMap(job.getEtfInstruments());

    Set<String> providers = new HashSet<>();
    map.values().forEach(pk -> providers.add(pk.provider()));

    assertThat(providers).containsExactlyInAnyOrder("DEUTSCHE_BOERSE", "EURONEXT", "EODHD");
  }

  @Test
  void buildKeyToProviderMap_excludesYahoo() {
    var job = jobOn(WED_0800_UTC);

    List<InstrumentReference> etfInstruments = job.getEtfInstruments();
    Map<String, PriceDataFreshnessAlertJob.ProviderKey> map =
        job.buildKeyToProviderMap(etfInstruments);

    assertThat(map.values()).noneMatch(pk -> pk.provider().equals("YAHOO"));
    assertThat(map.keySet())
        .doesNotContainAnyElementsOf(
            etfInstruments.stream().map(InstrumentReference::getYahooTicker).toList());
  }

  private void stubAllKeysWithDate(PriceDataFreshnessAlertJob job, LocalDate date) {
    Map<String, LocalDate> freshDates = buildAllFreshDates(job, date);
    when(fundValueRepository.findLatestDateByKeys(any())).thenReturn(freshDates);
  }

  private Map<String, LocalDate> buildAllFreshDates(
      PriceDataFreshnessAlertJob job, LocalDate date) {
    Map<String, LocalDate> result = new HashMap<>();
    job.buildKeyToProviderMap(job.getEtfInstruments())
        .keySet()
        .forEach(key -> result.put(key, date));
    return result;
  }

  private void makeEodhdStale(
      PriceDataFreshnessAlertJob job, Map<String, LocalDate> latestDates, LocalDate staleDate) {
    job.getEtfInstruments()
        .forEach(etfInstrument -> latestDates.put(etfInstrument.getEodhdTicker(), staleDate));
  }

  private void makeXetraStale(
      PriceDataFreshnessAlertJob job, Map<String, LocalDate> latestDates, LocalDate staleDate) {
    job.getEtfInstruments()
        .forEach(
            etfInstrument ->
                etfInstrument
                    .getXetraStorageKey()
                    .ifPresent(key -> latestDates.put(key, staleDate)));
  }

  private PriceDataFreshnessAlertJob jobOn(String instant) {
    Clock clock = Clock.fixed(Instant.parse(instant), TALLINN);
    return new PriceDataFreshnessAlertJob(
        fundValueRepository,
        notificationService,
        publicHolidays,
        instrumentReferenceService,
        clock);
  }
}
