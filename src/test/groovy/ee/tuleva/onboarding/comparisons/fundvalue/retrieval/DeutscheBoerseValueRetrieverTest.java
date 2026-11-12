package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import static java.math.BigDecimal.ZERO;
import static java.time.ZoneOffset.UTC;
import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import ee.tuleva.onboarding.time.ClockConfig;
import ee.tuleva.onboarding.time.ClockHolder;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;

@RestClientTest(DeutscheBoerseValueRetriever.class)
@Import(ClockConfig.class)
@TestPropertySource(properties = "deutsche-boerse.rate-limit-retry-delay=1ms")
class DeutscheBoerseValueRetrieverTest {

  @Autowired DeutscheBoerseValueRetriever retriever;

  @Autowired MockRestServiceServer server;

  @MockitoBean InstrumentReferenceService instrumentReferenceService;

  @MockitoBean FundValueRepository fundValueRepository;

  private static final List<String> XETRA_ISINS = List.of("IE00B4L5Y983", "IE00BFNM3G45");
  private static final ZoneId XETRA_TIMEZONE = ZoneId.of("Europe/Berlin");
  private static final String NO_BARS =
      """
      {"s": "no_data"}
      """;

  @BeforeEach
  void setUpInstruments() {
    given(instrumentReferenceService.getXetraIsins()).willReturn(XETRA_ISINS);
  }

  @AfterEach
  void cleanup() {
    server.reset();
    ClockHolder.setDefaultClock();
  }

  @Test
  void returnsCorrectKey() {
    assertThat(retriever.getKey()).isEqualTo("DEUTSCHE_BOERSE_VALUE");
  }

  @Test
  void exposesXetraStorageKeysAsExpectedStorageKeys() {
    assertThat(retriever.expectedStorageKeys())
        .containsExactlyInAnyOrderElementsOf(
            XETRA_ISINS.stream().map(isin -> isin + ".XETR").toList());
  }

  @Test
  void retrievesFundValuesFromDeutscheBoerseApi() {
    XETRA_ISINS.forEach(
        isin -> {
          server
              .expect(requestTo(chartHistoryUrl(isin, "2024-01-02", "2024-01-04")))
              .andRespond(
                  withSuccess(threeDailyBars("2024-01-02", "2024-01-04"), APPLICATION_JSON));
          server
              .expect(requestTo(priceInformationUrl(isin)))
              .andRespond(withSuccess("{}", APPLICATION_JSON));
        });

    var startDate = LocalDate.of(2024, 1, 2);
    var endDate = LocalDate.of(2024, 1, 4);

    List<FundValue> result = retriever.retrieveValuesForRange(startDate, endDate);

    assertThat(result)
        .isNotEmpty()
        .allSatisfy(
            fundValue -> {
              assertThat(fundValue.provider()).isEqualTo("DEUTSCHE_BOERSE");
              assertThat(fundValue.updatedAt()).isNotNull();
              assertThat(fundValue.key()).endsWith(".XETR");
            });
  }

  @Test
  void usesIsinAsKeyWithXetrSuffix() {
    var isin = XETRA_ISINS.getFirst();
    var mockResponse = bars(bar("2024-01-02", "102.82"));

    server
        .expect(requestTo(chartHistoryUrl(isin, "2024-01-02", "2024-01-02")))
        .andRespond(withSuccess(mockResponse, APPLICATION_JSON));
    server
        .expect(requestTo(priceInformationUrl(isin)))
        .andRespond(withSuccess("{}", APPLICATION_JSON));

    XETRA_ISINS.stream()
        .skip(1)
        .forEach(
            otherIsin -> {
              server
                  .expect(requestTo(chartHistoryUrl(otherIsin, "2024-01-02", "2024-01-02")))
                  .andRespond(withSuccess(NO_BARS, APPLICATION_JSON));
              server
                  .expect(requestTo(priceInformationUrl(otherIsin)))
                  .andRespond(withSuccess("{}", APPLICATION_JSON));
            });

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 2));

    var fundValue =
        result.stream()
            .filter(value -> value.key().equals(isin + ".XETR"))
            .findFirst()
            .orElseThrow();
    assertThat(fundValue.key()).isEqualTo(isin + ".XETR");
    assertThat(fundValue.value()).isEqualByComparingTo(new BigDecimal("102.82"));
    assertThat(fundValue.date()).isEqualTo(LocalDate.of(2024, 1, 2));
  }

  @Test
  void filtersOutZeroValues() {
    XETRA_ISINS.forEach(
        isin -> {
          server
              .expect(requestTo(chartHistoryUrl(isin, "2024-01-02", "2024-01-04")))
              .andRespond(withSuccess(barsWithZeroClose(), APPLICATION_JSON));
          server
              .expect(requestTo(priceInformationUrl(isin)))
              .andRespond(withSuccess("{}", APPLICATION_JSON));
        });

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 4));

    assertThat(result)
        .isNotEmpty()
        .allSatisfy(fundValue -> assertThat(fundValue.value()).isNotEqualTo(ZERO));
  }

  @Test
  void returnsEmptyListOnApiError() {
    XETRA_ISINS.forEach(
        isin ->
            server
                .expect(requestTo(chartHistoryUrl(isin, "2024-01-02", "2024-01-04")))
                .andRespond(withServerError()));

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 4));

    assertThat(result).isEmpty();
  }

  @Test
  void handlesEmptyDataResponse() {
    XETRA_ISINS.forEach(
        isin -> {
          server
              .expect(requestTo(chartHistoryUrl(isin, "2024-01-02", "2024-01-02")))
              .andRespond(withSuccess(NO_BARS, APPLICATION_JSON));
          server
              .expect(requestTo(priceInformationUrl(isin)))
              .andRespond(withSuccess("{}", APPLICATION_JSON));
        });

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 2));

    assertThat(result).isEmpty();
  }

  @Test
  void excludesYesterdaysDataBefore0600CET() {
    // 2024-01-05 04:00 UTC = 05:00 CET (before 06:00 CET cutoff)
    ClockHolder.setClock(Clock.fixed(Instant.parse("2024-01-05T04:00:00Z"), UTC));

    XETRA_ISINS.forEach(
        isin -> {
          server
              .expect(requestTo(chartHistoryUrl(isin, "2024-01-02", "2024-01-04")))
              .andRespond(
                  withSuccess(threeDailyBars("2024-01-02", "2024-01-04"), APPLICATION_JSON));
          server
              .expect(requestTo(priceInformationUrl(isin)))
              .andRespond(
                  withSuccess(
                      priceInformationResponse(isin, "102.00", "2024-01-04T16:35:00Z"),
                      APPLICATION_JSON));
        });

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 4));

    // Before 06:00 CET on Jan 5: latestFinalizedDate = Jan 3 (2 days ago)
    assertThat(result)
        .isNotEmpty()
        .anyMatch(fundValue -> fundValue.date().equals(LocalDate.of(2024, 1, 2)))
        .anyMatch(fundValue -> fundValue.date().equals(LocalDate.of(2024, 1, 3)))
        .noneMatch(fundValue -> fundValue.date().equals(LocalDate.of(2024, 1, 4)));
  }

  @Test
  void includesYesterdaysDataAfter0600CET() {
    // 2024-01-05 05:00 UTC = 06:00 CET (at/after 06:00 CET cutoff)
    ClockHolder.setClock(Clock.fixed(Instant.parse("2024-01-05T05:00:00Z"), UTC));

    XETRA_ISINS.forEach(
        isin -> {
          server
              .expect(requestTo(chartHistoryUrl(isin, "2024-01-02", "2024-01-04")))
              .andRespond(
                  withSuccess(threeDailyBars("2024-01-02", "2024-01-04"), APPLICATION_JSON));
          server
              .expect(requestTo(priceInformationUrl(isin)))
              .andRespond(
                  withSuccess(
                      priceInformationResponse(isin, "102.00", "2024-01-04T16:35:00Z"),
                      APPLICATION_JSON));
        });

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 4));

    // After 06:00 CET on Jan 5: latestFinalizedDate = Jan 4 (yesterday)
    assertThat(result)
        .isNotEmpty()
        .anyMatch(fundValue -> fundValue.date().equals(LocalDate.of(2024, 1, 2)))
        .anyMatch(fundValue -> fundValue.date().equals(LocalDate.of(2024, 1, 3)))
        .anyMatch(fundValue -> fundValue.date().equals(LocalDate.of(2024, 1, 4)));
  }

  @Test
  void alwaysExcludesTodaysData() {
    // 2024-01-04 20:00 UTC = 21:00 CET (well after any market close)
    ClockHolder.setClock(Clock.fixed(Instant.parse("2024-01-04T20:00:00Z"), UTC));

    XETRA_ISINS.forEach(
        isin -> {
          server
              .expect(requestTo(chartHistoryUrl(isin, "2024-01-02", "2024-01-04")))
              .andRespond(
                  withSuccess(threeDailyBars("2024-01-02", "2024-01-04"), APPLICATION_JSON));
          server
              .expect(requestTo(priceInformationUrl(isin)))
              .andRespond(
                  withSuccess(
                      priceInformationResponse(isin, "102.00", "2024-01-04T16:35:00Z"),
                      APPLICATION_JSON));
        });

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 4));

    // At 21:00 CET on Jan 4: latestFinalizedDate = Jan 3 (yesterday)
    // Jan 4 (today) is always excluded
    assertThat(result)
        .isNotEmpty()
        .anyMatch(fundValue -> fundValue.date().equals(LocalDate.of(2024, 1, 2)))
        .anyMatch(fundValue -> fundValue.date().equals(LocalDate.of(2024, 1, 3)))
        .noneMatch(fundValue -> fundValue.date().equals(LocalDate.of(2024, 1, 4)));
  }

  @Test
  void overridesBarCloseWithOfficialLastPriceForItsDate() {
    // 2026-07-21 05:00 UTC = 07:00 CEST, after the 06:00 cutoff: 2026-07-20 is finalized
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-07-21T05:00:00Z"), UTC));
    var isin = XETRA_ISINS.getFirst();
    var thinDayBars = bars(bar("2026-07-17", "9.92"), bar("2026-07-20", "9.969"));

    server
        .expect(requestTo(chartHistoryUrl(isin, "2026-07-17", "2026-07-20")))
        .andRespond(withSuccess(thinDayBars, APPLICATION_JSON));
    server
        .expect(requestTo(priceInformationUrl(isin)))
        .andRespond(
            withSuccess(
                priceInformationResponse(isin, "9.947", "2026-07-20T15:35:48Z"), APPLICATION_JSON));
    expectNoDataForOtherIsins(isin, "2026-07-17", "2026-07-20");

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2026, 7, 17), LocalDate.of(2026, 7, 20));

    var updatedAt = Instant.parse("2026-07-21T05:00:00Z");
    assertThat(result.stream().filter(value -> value.key().equals(isin + ".XETR")))
        .containsExactlyInAnyOrder(
            new FundValue(
                isin + ".XETR",
                LocalDate.of(2026, 7, 17),
                new BigDecimal("9.92"),
                "DEUTSCHE_BOERSE",
                updatedAt),
            new FundValue(
                isin + ".XETR",
                LocalDate.of(2026, 7, 20),
                new BigDecimal("9.947"),
                "DEUTSCHE_BOERSE",
                updatedAt));
  }

  @Test
  void ignoresLiveIntradayLastPriceFromToday() {
    // 2026-07-21 08:00 UTC = 10:00 CEST, Xetra is open and price information serves a live price
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-07-21T08:00:00Z"), UTC));
    var isin = XETRA_ISINS.getFirst();
    var bars = bars(bar("2026-07-20", "9.969"));

    server
        .expect(requestTo(chartHistoryUrl(isin, "2026-07-17", "2026-07-21")))
        .andRespond(withSuccess(bars, APPLICATION_JSON));
    server
        .expect(requestTo(priceInformationUrl(isin)))
        .andRespond(
            withSuccess(
                priceInformationResponse(isin, "9.943", "2026-07-21T07:16:42Z"), APPLICATION_JSON));
    expectNoDataForOtherIsins(isin, "2026-07-17", "2026-07-21");

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2026, 7, 17), LocalDate.of(2026, 7, 21));

    assertThat(result.stream().filter(value -> value.key().equals(isin + ".XETR")))
        .containsExactlyInAnyOrder(
            new FundValue(
                isin + ".XETR",
                LocalDate.of(2026, 7, 20),
                new BigDecimal("9.969"),
                "DEUTSCHE_BOERSE",
                Instant.parse("2026-07-21T08:00:00Z")));
  }

  @Test
  void keepsBarCloseWhenLastPriceIsRetailSessionPrintFromPreviousEvening() {
    // 2026-07-22 04:00 UTC = 06:00 CEST: 2026-07-21 is finalized, price information still shows a
    // retail session print from 20:05 CEST the previous evening
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-07-22T04:00:00Z"), UTC));
    var isin = XETRA_ISINS.getFirst();
    var bars = bars(bar("2026-07-20", "8.81"), bar("2026-07-21", "9.002"));

    server
        .expect(requestTo(chartHistoryUrl(isin, "2026-07-20", "2026-07-21")))
        .andRespond(withSuccess(bars, APPLICATION_JSON));
    server
        .expect(requestTo(priceInformationUrl(isin)))
        .andRespond(
            withSuccess(
                priceInformationResponse(isin, "9.045", "2026-07-21T18:05:14Z", "R"),
                APPLICATION_JSON));
    expectNoDataForOtherIsins(isin, "2026-07-20", "2026-07-21");

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 21));

    var updatedAt = Instant.parse("2026-07-22T04:00:00Z");
    assertThat(result.stream().filter(value -> value.key().equals(isin + ".XETR")))
        .containsExactlyInAnyOrder(
            new FundValue(
                isin + ".XETR",
                LocalDate.of(2026, 7, 20),
                new BigDecimal("8.81"),
                "DEUTSCHE_BOERSE",
                updatedAt),
            new FundValue(
                isin + ".XETR",
                LocalDate.of(2026, 7, 21),
                new BigDecimal("9.002"),
                "DEUTSCHE_BOERSE",
                updatedAt));
  }

  @Test
  void doesNotHoldBackBarCloseWhenPriceInformationServesRetailPrintFromToday() {
    // 2026-07-22 06:30 UTC = 08:30 CEST: the retail early session prints before Xetra opens
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-07-22T06:30:00Z"), UTC));
    var isin = XETRA_ISINS.getFirst();
    var bars = bars(bar("2026-07-20", "8.81"), bar("2026-07-21", "9.002"));

    server
        .expect(requestTo(chartHistoryUrl(isin, "2026-07-20", "2026-07-21")))
        .andRespond(withSuccess(bars, APPLICATION_JSON));
    server
        .expect(requestTo(priceInformationUrl(isin)))
        .andRespond(
            withSuccess(
                priceInformationResponse(isin, "9.058", "2026-07-22T06:01:29Z", "R"),
                APPLICATION_JSON));
    expectNoDataForOtherIsins(isin, "2026-07-20", "2026-07-21");

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 21));

    var updatedAt = Instant.parse("2026-07-22T06:30:00Z");
    assertThat(result.stream().filter(value -> value.key().equals(isin + ".XETR")))
        .containsExactlyInAnyOrder(
            new FundValue(
                isin + ".XETR",
                LocalDate.of(2026, 7, 20),
                new BigDecimal("8.81"),
                "DEUTSCHE_BOERSE",
                updatedAt),
            new FundValue(
                isin + ".XETR",
                LocalDate.of(2026, 7, 21),
                new BigDecimal("9.002"),
                "DEUTSCHE_BOERSE",
                updatedAt));
  }

  @Test
  void keepsOlderBarsButHoldsBackLatestFinalizedDayWhenPriceInformationIsUnavailable() {
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-07-22T05:00:00Z"), UTC));
    var isin = XETRA_ISINS.getFirst();
    server
        .expect(requestTo(chartHistoryUrl(isin, "2026-07-17", "2026-07-21")))
        .andRespond(
            withSuccess(
                bars(
                    bar("2026-07-17", "9.92"),
                    bar("2026-07-20", "9.969"),
                    bar("2026-07-21", "9.951")),
                APPLICATION_JSON));
    server.expect(requestTo(priceInformationUrl(isin))).andRespond(withServerError());
    expectNoDataForOtherIsins(isin, "2026-07-17", "2026-07-21");

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2026, 7, 17), LocalDate.of(2026, 7, 21));

    var updatedAt = Instant.parse("2026-07-22T05:00:00Z");
    assertThat(result)
        .containsExactlyInAnyOrder(
            new FundValue(
                isin + ".XETR",
                LocalDate.of(2026, 7, 17),
                new BigDecimal("9.92"),
                "DEUTSCHE_BOERSE",
                updatedAt),
            new FundValue(
                isin + ".XETR",
                LocalDate.of(2026, 7, 20),
                new BigDecimal("9.969"),
                "DEUTSCHE_BOERSE",
                updatedAt));
  }

  @Test
  void holdsBackLatestFinalizedDayWhenPriceInformationHasNoUsableLastPrice() {
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-07-21T05:00:00Z"), UTC));
    var isin = XETRA_ISINS.getFirst();
    var bars = bars(bar("2026-07-17", "9.92"), bar("2026-07-20", "9.969"));

    server
        .expect(requestTo(chartHistoryUrl(isin, "2026-07-17", "2026-07-20")))
        .andRespond(withSuccess(bars, APPLICATION_JSON));
    server
        .expect(requestTo(priceInformationUrl(isin)))
        .andRespond(withSuccess("{}", APPLICATION_JSON));
    expectNoDataForOtherIsins(isin, "2026-07-17", "2026-07-20");

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2026, 7, 17), LocalDate.of(2026, 7, 20));

    assertThat(result.stream().filter(value -> value.key().equals(isin + ".XETR")))
        .containsExactlyInAnyOrder(
            new FundValue(
                isin + ".XETR",
                LocalDate.of(2026, 7, 17),
                new BigDecimal("9.92"),
                "DEUTSCHE_BOERSE",
                Instant.parse("2026-07-21T05:00:00Z")));
  }

  @Test
  void usesOfficialLastPriceWhenChartHistoryHasNoBars() {
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-07-21T05:00:00Z"), UTC));
    var isin = XETRA_ISINS.getFirst();

    server
        .expect(requestTo(chartHistoryUrl(isin, "2026-07-17", "2026-07-20")))
        .andRespond(withSuccess(NO_BARS, APPLICATION_JSON));
    server
        .expect(requestTo(priceInformationUrl(isin)))
        .andRespond(
            withSuccess(
                priceInformationResponse(isin, "9.947", "2026-07-20T15:35:48Z"), APPLICATION_JSON));
    expectNoDataForOtherIsins(isin, "2026-07-17", "2026-07-20");

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2026, 7, 17), LocalDate.of(2026, 7, 20));

    assertThat(result.stream().filter(value -> value.key().equals(isin + ".XETR")))
        .containsExactlyInAnyOrder(
            new FundValue(
                isin + ".XETR",
                LocalDate.of(2026, 7, 20),
                new BigDecimal("9.947"),
                "DEUTSCHE_BOERSE",
                Instant.parse("2026-07-21T05:00:00Z")));
  }

  @Test
  void skipsIsinAlreadyStoredThroughLatestFinalizedDay() {
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-07-21T05:00:00Z"), UTC));
    var storedIsin = XETRA_ISINS.getFirst();
    var staleIsin = XETRA_ISINS.getLast();
    given(
            fundValueRepository.findLatestDateByKeys(
                Set.of(storedIsin + ".XETR", staleIsin + ".XETR")))
        .willReturn(
            Map.of(
                storedIsin + ".XETR", LocalDate.of(2026, 7, 20),
                staleIsin + ".XETR", LocalDate.of(2026, 7, 17)));
    server
        .expect(requestTo(chartHistoryUrl(staleIsin, "2026-07-17", "2026-07-20")))
        .andRespond(withSuccess(NO_BARS, APPLICATION_JSON));
    server
        .expect(requestTo(priceInformationUrl(staleIsin)))
        .andRespond(
            withSuccess(
                priceInformationResponse(staleIsin, "9.947", "2026-07-20T15:35:48Z"),
                APPLICATION_JSON));

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2026, 7, 17), LocalDate.of(2026, 7, 20));

    assertThat(result)
        .containsExactly(
            new FundValue(
                staleIsin + ".XETR",
                LocalDate.of(2026, 7, 20),
                new BigDecimal("9.947"),
                "DEUTSCHE_BOERSE",
                Instant.parse("2026-07-21T05:00:00Z")));
    server.verify();
  }

  @Test
  void skipsIsinStoredThroughFridayUntilMondayCloseIsFinalized() {
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-07-20T05:00:00Z"), UTC));
    var storedIsin = XETRA_ISINS.getFirst();
    var staleIsin = XETRA_ISINS.getLast();
    given(
            fundValueRepository.findLatestDateByKeys(
                Set.of(storedIsin + ".XETR", staleIsin + ".XETR")))
        .willReturn(
            Map.of(
                storedIsin + ".XETR", LocalDate.of(2026, 7, 17),
                staleIsin + ".XETR", LocalDate.of(2026, 7, 16)));
    var bars = bars(bar("2026-07-17", "9.92"));
    server
        .expect(requestTo(chartHistoryUrl(staleIsin, "2026-07-17", "2026-07-20")))
        .andRespond(withSuccess(bars, APPLICATION_JSON));
    server
        .expect(requestTo(priceInformationUrl(staleIsin)))
        .andRespond(withSuccess("{}", APPLICATION_JSON));

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2026, 7, 17), LocalDate.of(2026, 7, 20));

    assertThat(result)
        .containsExactly(
            new FundValue(
                staleIsin + ".XETR",
                LocalDate.of(2026, 7, 17),
                new BigDecimal("9.92"),
                "DEUTSCHE_BOERSE",
                Instant.parse("2026-07-20T05:00:00Z")));
    server.verify();
  }

  @Test
  void retriesRateLimitedRequestsUntilTheyGoThrough() {
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-07-21T05:00:00Z"), UTC));
    var isin = XETRA_ISINS.getFirst();
    server
        .expect(requestTo(chartHistoryUrl(isin, "2026-07-17", "2026-07-20")))
        .andRespond(withStatus(SERVICE_UNAVAILABLE));
    server
        .expect(requestTo(chartHistoryUrl(isin, "2026-07-17", "2026-07-20")))
        .andRespond(withSuccess(NO_BARS, APPLICATION_JSON));
    server.expect(requestTo(priceInformationUrl(isin))).andRespond(withStatus(SERVICE_UNAVAILABLE));
    server
        .expect(requestTo(priceInformationUrl(isin)))
        .andRespond(
            withSuccess(
                priceInformationResponse(isin, "9.947", "2026-07-20T15:35:48Z"), APPLICATION_JSON));
    expectNoDataForOtherIsins(isin, "2026-07-17", "2026-07-20");

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2026, 7, 17), LocalDate.of(2026, 7, 20));

    assertThat(result)
        .containsExactly(
            new FundValue(
                isin + ".XETR",
                LocalDate.of(2026, 7, 20),
                new BigDecimal("9.947"),
                "DEUTSCHE_BOERSE",
                Instant.parse("2026-07-21T05:00:00Z")));
    server.verify();
  }

  @Test
  void defersRemainingIsinsToNextRunWhenRateLimitPersists() {
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-07-21T05:00:00Z"), UTC));
    var fetchedIsin = "IE00B4L5Y983";
    var rateLimitedIsin = "IE00BFNM3G45";
    var deferredIsin = "IE00B5BMR087";
    given(instrumentReferenceService.getXetraIsins())
        .willReturn(List.of(fetchedIsin, rateLimitedIsin, deferredIsin));
    server
        .expect(requestTo(chartHistoryUrl(fetchedIsin, "2026-07-17", "2026-07-20")))
        .andRespond(withSuccess(NO_BARS, APPLICATION_JSON));
    server
        .expect(requestTo(priceInformationUrl(fetchedIsin)))
        .andRespond(
            withSuccess(
                priceInformationResponse(fetchedIsin, "9.947", "2026-07-20T15:35:48Z"),
                APPLICATION_JSON));
    server
        .expect(times(4), requestTo(chartHistoryUrl(rateLimitedIsin, "2026-07-17", "2026-07-20")))
        .andRespond(withStatus(SERVICE_UNAVAILABLE));

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2026, 7, 17), LocalDate.of(2026, 7, 20));

    assertThat(result)
        .containsExactly(
            new FundValue(
                fetchedIsin + ".XETR",
                LocalDate.of(2026, 7, 20),
                new BigDecimal("9.947"),
                "DEUTSCHE_BOERSE",
                Instant.parse("2026-07-21T05:00:00Z")));
    server.verify();
  }

  @Test
  void defersRemainingIsinsToNextRunWhenPriceInformationRateLimitPersists() {
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-07-21T05:00:00Z"), UTC));
    var fetchedIsin = "IE00B4L5Y983";
    var rateLimitedIsin = "IE00BFNM3G45";
    var deferredIsin = "IE00B5BMR087";
    given(instrumentReferenceService.getXetraIsins())
        .willReturn(List.of(fetchedIsin, rateLimitedIsin, deferredIsin));
    server
        .expect(requestTo(chartHistoryUrl(fetchedIsin, "2026-07-17", "2026-07-20")))
        .andRespond(withSuccess(NO_BARS, APPLICATION_JSON));
    server
        .expect(requestTo(priceInformationUrl(fetchedIsin)))
        .andRespond(
            withSuccess(
                priceInformationResponse(fetchedIsin, "9.947", "2026-07-20T15:35:48Z"),
                APPLICATION_JSON));
    server
        .expect(requestTo(chartHistoryUrl(rateLimitedIsin, "2026-07-17", "2026-07-20")))
        .andRespond(withSuccess(NO_BARS, APPLICATION_JSON));
    server
        .expect(times(4), requestTo(priceInformationUrl(rateLimitedIsin)))
        .andRespond(withStatus(SERVICE_UNAVAILABLE));

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2026, 7, 17), LocalDate.of(2026, 7, 20));

    assertThat(result)
        .containsExactly(
            new FundValue(
                fetchedIsin + ".XETR",
                LocalDate.of(2026, 7, 20),
                new BigDecimal("9.947"),
                "DEUTSCHE_BOERSE",
                Instant.parse("2026-07-21T05:00:00Z")));
    server.verify();
  }

  @Test
  void readsDailyBarsForWholeXetraDaysDatedInXetraTimezone() {
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-11-12T05:00:00Z"), UTC));
    var isin = XETRA_ISINS.getFirst();
    server
        .expect(
            requestTo(
                "https://api.live.deutsche-boerse.com/v1/tradingview/history?symbol=XETR:"
                    + isin
                    + "&resolution=1D&from=1794265200&to=1794437999"))
        .andRespond(
            withSuccess(
                """
                {"s":"ok","c":[128.65,130.955],"h":[129.1,131.01],"l":[128.23,129.825],"o":[128.965,129.855],"t":[1794272400,1794358800],"v":[50419592.71,25056851.95]}
                """,
                APPLICATION_JSON));
    server
        .expect(requestTo(priceInformationUrl(isin)))
        .andRespond(
            withSuccess(
                priceInformationResponse(isin, "130.79", "2026-11-12T08:25:13+01:00", "R"),
                APPLICATION_JSON));
    expectNoDataForOtherIsins(isin, "2026-11-10", "2026-11-11");

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 11));

    var updatedAt = Instant.parse("2026-11-12T05:00:00Z");
    assertThat(result)
        .containsExactlyInAnyOrder(
            new FundValue(
                isin + ".XETR",
                LocalDate.of(2026, 11, 10),
                new BigDecimal("128.65"),
                "DEUTSCHE_BOERSE",
                updatedAt),
            new FundValue(
                isin + ".XETR",
                LocalDate.of(2026, 11, 11),
                new BigDecimal("130.955"),
                "DEUTSCHE_BOERSE",
                updatedAt));
  }

  @Test
  void overridesBarCloseWithOfficialLastPriceStampedWithUtcOffset() {
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-11-12T05:00:00Z"), UTC));
    var isin = XETRA_ISINS.getFirst();
    server
        .expect(requestTo(chartHistoryUrl(isin, "2026-11-10", "2026-11-11")))
        .andRespond(
            withSuccess(
                bars(bar("2026-11-10", "49.3"), bar("2026-11-11", "50.48")), APPLICATION_JSON));
    server
        .expect(requestTo(priceInformationUrl(isin)))
        .andRespond(
            withSuccess(
                priceInformationResponse(isin, "50.49", "2026-11-11T17:35:50+01:00"),
                APPLICATION_JSON));
    expectNoDataForOtherIsins(isin, "2026-11-10", "2026-11-11");

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 11));

    var updatedAt = Instant.parse("2026-11-12T05:00:00Z");
    assertThat(result)
        .containsExactlyInAnyOrder(
            new FundValue(
                isin + ".XETR",
                LocalDate.of(2026, 11, 10),
                new BigDecimal("49.3"),
                "DEUTSCHE_BOERSE",
                updatedAt),
            new FundValue(
                isin + ".XETR",
                LocalDate.of(2026, 11, 11),
                new BigDecimal("50.49"),
                "DEUTSCHE_BOERSE",
                updatedAt));
  }

  @Test
  void overridesRoundedBarWithOfficialCloseAtFullPrecision() {
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-11-12T05:00:00Z"), UTC));
    var isin = XETRA_ISINS.getFirst();
    server
        .expect(requestTo(chartHistoryUrl(isin, "2026-11-10", "2026-11-11")))
        .andRespond(
            withSuccess(
                bars(bar("2026-11-10", "4.876"), bar("2026-11-11", "4.875")), APPLICATION_JSON));
    server
        .expect(requestTo(priceInformationUrl(isin)))
        .andRespond(
            withSuccess(
                """
                {"changeToPrevDayAbsolute":-0.0008,"changeToPrevDayInPercent":-0.0200,"closingPricePrevTradingDay":4.8748,"currency":{"originalValue":"EUR","translations":{"others":"Euro"}},"dayHigh":4.8817,"dayLow":4.8643,"isin":"IE00BDBRDM35","lastPrice":4.8748,"lastPriceIndicator":null,"mic":"XETR","minimumTradableUnit":null,"priceFixings":111,"timestampLastPrice":"2026-11-11T17:36:05+01:00","tradedInPercent":false,"tradingTimeEnd":"22:00:00","tradingTimeStart":"08:00:00","turnoverInEur":711893.1699,"turnoverInPieces":146009,"turnoverNominal":null,"weeks52High":5.0256,"weeks52Low":4.78}
                """,
                APPLICATION_JSON));
    expectNoDataForOtherIsins(isin, "2026-11-10", "2026-11-11");

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 11));

    var updatedAt = Instant.parse("2026-11-12T05:00:00Z");
    assertThat(result)
        .containsExactlyInAnyOrder(
            new FundValue(
                isin + ".XETR",
                LocalDate.of(2026, 11, 10),
                new BigDecimal("4.876"),
                "DEUTSCHE_BOERSE",
                updatedAt),
            new FundValue(
                isin + ".XETR",
                LocalDate.of(2026, 11, 11),
                new BigDecimal("4.8748"),
                "DEUTSCHE_BOERSE",
                updatedAt));
  }

  @Test
  void keepsRoundedBarClosesAfterRetailPrint() {
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-11-12T05:00:00Z"), UTC));
    var isin = XETRA_ISINS.getFirst();
    server
        .expect(requestTo(chartHistoryUrl(isin, "2026-11-10", "2026-11-11")))
        .andRespond(
            withSuccess(
                bars(bar("2026-11-10", "4.876"), bar("2026-11-11", "4.875")), APPLICATION_JSON));
    server
        .expect(requestTo(priceInformationUrl(isin)))
        .andRespond(
            withSuccess(
                """
                {
                  "isin": "%s",
                  "mic": "XETR",
                  "lastPrice": 4.87,
                  "lastPriceIndicator": "R",
                  "timestampLastPrice": "2026-11-11T21:48:36+01:00",
                  "dayHigh": 4.8817,
                  "dayLow": 4.8643
                }
                """
                    .formatted(isin),
                APPLICATION_JSON));
    expectNoDataForOtherIsins(isin, "2026-11-10", "2026-11-11");

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 11));

    var updatedAt = Instant.parse("2026-11-12T05:00:00Z");
    assertThat(result)
        .containsExactlyInAnyOrder(
            new FundValue(
                isin + ".XETR",
                LocalDate.of(2026, 11, 10),
                new BigDecimal("4.876"),
                "DEUTSCHE_BOERSE",
                updatedAt),
            new FundValue(
                isin + ".XETR",
                LocalDate.of(2026, 11, 11),
                new BigDecimal("4.875"),
                "DEUTSCHE_BOERSE",
                updatedAt));
  }

  @Test
  void requestsWholeXetraDaysInSummerTime() {
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-07-21T05:00:00Z"), UTC));
    var isin = XETRA_ISINS.getFirst();
    server
        .expect(
            requestTo(
                "https://api.live.deutsche-boerse.com/v1/tradingview/history?symbol=XETR:"
                    + isin
                    + "&resolution=1D&from=1784239200&to=1784584799"))
        .andRespond(withSuccess(NO_BARS, APPLICATION_JSON));
    server
        .expect(requestTo(priceInformationUrl(isin)))
        .andRespond(withSuccess("{}", APPLICATION_JSON));
    expectNoDataForOtherIsins(isin, "2026-07-17", "2026-07-20");

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2026, 7, 17), LocalDate.of(2026, 7, 20));

    assertThat(result).isEmpty();
    server.verify();
  }

  @Test
  void requestsAtMostNineYearsOfBarsWhenBackfillingFromEarliestDate() {
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-11-12T05:00:00Z"), UTC));
    XETRA_ISINS.forEach(
        isin -> {
          server
              .expect(requestTo(chartHistoryUrl(isin, "2017-11-11", "2026-11-11")))
              .andRespond(withSuccess(NO_BARS, APPLICATION_JSON));
          server
              .expect(requestTo(priceInformationUrl(isin)))
              .andRespond(withSuccess("{}", APPLICATION_JSON));
        });

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2003, 1, 7), LocalDate.of(2026, 11, 11));

    assertThat(result).isEmpty();
    server.verify();
  }

  @Test
  void ignoresBarsBeforeRequestedStartDate() {
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-07-21T05:00:00Z"), UTC));
    var isin = XETRA_ISINS.getFirst();
    server
        .expect(requestTo(chartHistoryUrl(isin, "2026-07-17", "2026-07-20")))
        .andRespond(
            withSuccess(
                bars(bar("2026-07-16", "9.91"), bar("2026-07-17", "9.92")), APPLICATION_JSON));
    server
        .expect(requestTo(priceInformationUrl(isin)))
        .andRespond(withSuccess("{}", APPLICATION_JSON));
    expectNoDataForOtherIsins(isin, "2026-07-17", "2026-07-20");

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2026, 7, 17), LocalDate.of(2026, 7, 20));

    assertThat(result)
        .containsExactly(
            new FundValue(
                isin + ".XETR",
                LocalDate.of(2026, 7, 17),
                new BigDecimal("9.92"),
                "DEUTSCHE_BOERSE",
                Instant.parse("2026-07-21T05:00:00Z")));
  }

  @Test
  void skipsBarsWithoutClose() {
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-07-21T05:00:00Z"), UTC));
    var isin = XETRA_ISINS.getFirst();
    server
        .expect(requestTo(chartHistoryUrl(isin, "2026-07-17", "2026-07-20")))
        .andRespond(
            withSuccess(
                """
                {"s": "ok", "t": [1784246400, 1784505600], "c": [null, 9.969]}
                """,
                APPLICATION_JSON));
    server
        .expect(requestTo(priceInformationUrl(isin)))
        .andRespond(
            withSuccess(
                priceInformationResponse(isin, "9.969", "2026-07-20T17:35:48+02:00"),
                APPLICATION_JSON));
    expectNoDataForOtherIsins(isin, "2026-07-17", "2026-07-20");

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2026, 7, 17), LocalDate.of(2026, 7, 20));

    assertThat(result)
        .containsExactly(
            new FundValue(
                isin + ".XETR",
                LocalDate.of(2026, 7, 20),
                new BigDecimal("9.969"),
                "DEUTSCHE_BOERSE",
                Instant.parse("2026-07-21T05:00:00Z")));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"s\": \"error\"}",
        "{\"s\": \"ok\", \"t\": [1784160000], \"c\": []}",
        "{\"s\": \"ok\"}",
        ""
      })
  void skipsIsinWhoseChartHistoryIsMalformedAndKeepsTheOthers(String malformedChartHistory) {
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-07-21T05:00:00Z"), UTC));
    var malformedIsin = XETRA_ISINS.getFirst();
    var healthyIsin = XETRA_ISINS.getLast();
    server
        .expect(requestTo(chartHistoryUrl(malformedIsin, "2026-07-17", "2026-07-20")))
        .andRespond(withSuccess(malformedChartHistory, APPLICATION_JSON));
    server
        .expect(requestTo(chartHistoryUrl(healthyIsin, "2026-07-17", "2026-07-20")))
        .andRespond(withSuccess(bars(bar("2026-07-17", "9.92")), APPLICATION_JSON));
    server
        .expect(requestTo(priceInformationUrl(healthyIsin)))
        .andRespond(withSuccess("{}", APPLICATION_JSON));

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2026, 7, 17), LocalDate.of(2026, 7, 20));

    assertThat(result)
        .containsExactly(
            new FundValue(
                healthyIsin + ".XETR",
                LocalDate.of(2026, 7, 17),
                new BigDecimal("9.92"),
                "DEUTSCHE_BOERSE",
                Instant.parse("2026-07-21T05:00:00Z")));
    server.verify();
  }

  private void expectNoDataForOtherIsins(String isin, String startDate, String endDate) {
    XETRA_ISINS.stream()
        .filter(otherIsin -> !otherIsin.equals(isin))
        .forEach(
            otherIsin -> {
              server
                  .expect(requestTo(chartHistoryUrl(otherIsin, startDate, endDate)))
                  .andRespond(withSuccess(NO_BARS, APPLICATION_JSON));
              server
                  .expect(requestTo(priceInformationUrl(otherIsin)))
                  .andRespond(withSuccess("{}", APPLICATION_JSON));
            });
  }

  private static String chartHistoryUrl(String isin, String startDate, String endDate) {
    return "https://api.live.deutsche-boerse.com/v1/tradingview/history?symbol=XETR:"
        + isin
        + "&resolution=1D&from="
        + LocalDate.parse(startDate).atStartOfDay(XETRA_TIMEZONE).toEpochSecond()
        + "&to="
        + (LocalDate.parse(endDate).plusDays(1).atStartOfDay(XETRA_TIMEZONE).toEpochSecond() - 1);
  }

  private static String priceInformationUrl(String isin) {
    return "https://api.live.deutsche-boerse.com/v1/data/price_information/single?isin="
        + isin
        + "&mic=XETR";
  }

  private static String priceInformationResponse(
      String isin, String lastPrice, String timestampLastPrice) {
    return """
        {
          "isin": "%s",
          "mic": "XETR",
          "lastPrice": %s,
          "lastPriceIndicator": null,
          "timestampLastPrice": "%s"
        }
        """
        .formatted(isin, lastPrice, timestampLastPrice);
  }

  private static String priceInformationResponse(
      String isin, String lastPrice, String timestampLastPrice, String lastPriceIndicator) {
    return """
        {
          "isin": "%s",
          "mic": "XETR",
          "lastPrice": %s,
          "lastPriceIndicator": "%s",
          "timestampLastPrice": "%s"
        }
        """
        .formatted(isin, lastPrice, lastPriceIndicator, timestampLastPrice);
  }

  private record Bar(String date, String close) {}

  private static Bar bar(String date, String close) {
    return new Bar(date, close);
  }

  private static String bars(Bar... bars) {
    var timestamps =
        Arrays.stream(bars)
            .map(
                bar ->
                    String.valueOf(
                        LocalDate.parse(bar.date())
                            .atTime(2, 0)
                            .atZone(XETRA_TIMEZONE)
                            .toEpochSecond()))
            .collect(joining(", "));
    var closes = Arrays.stream(bars).map(Bar::close).collect(joining(", "));
    return """
        {"s": "ok", "t": [%s], "c": [%s]}
        """
        .formatted(timestamps, closes);
  }

  private static String threeDailyBars(String startDate, String endDate) {
    return bars(bar(startDate, "100.50"), bar("2024-01-03", "101.25"), bar(endDate, "102.00"));
  }

  private static String barsWithZeroClose() {
    return bars(bar("2024-01-02", "100.50"), bar("2024-01-03", "0.0"), bar("2024-01-04", "102.00"));
  }
}
