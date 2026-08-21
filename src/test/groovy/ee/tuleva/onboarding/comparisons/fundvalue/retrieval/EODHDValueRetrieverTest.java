package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import static java.math.BigDecimal.ZERO;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueProvider;
import ee.tuleva.onboarding.time.ClockConfig;
import ee.tuleva.onboarding.time.ClockHolder;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;

@RestClientTest(EODHDValueRetriever.class)
@Import(ClockConfig.class)
@TestPropertySource(properties = "eodhd.api-token=test-token")
class EODHDValueRetrieverTest {

  @Autowired EODHDValueRetriever retriever;

  @Autowired MockRestServiceServer server;

  @MockitoBean FundValueProvider fundValueProvider;

  @AfterEach
  void cleanup() {
    server.reset();
    ClockHolder.setDefaultClock();
  }

  @Test
  void returnsCorrectKey() {
    assertThat(retriever.getKey()).isEqualTo("EODHD_VALUE");
  }

  @Test
  void exposesAllEodhdTickersAndForexAsExpectedStorageKeys() {
    assertThat(retriever.expectedStorageKeys())
        .containsAll(FundTicker.getEodhdTickers())
        .contains("EURUSD.FOREX");
  }

  @Test
  void stripsProviderSuffixFromApiCallButKeepsItInStoredKey() {
    var mockResponse =
        """
        [
          {"date": "2024-01-02", "open": 4.50, "high": 4.55, "low": 4.45, "close": 4.52, "adjusted_close": 4.52, "volume": 1000}
        ]
        """;

    FundTicker.getEodhdTickers()
        .forEach(
            ticker ->
                server
                    .expect(
                        requestTo(
                            expectedUri(
                                ticker, LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 2))))
                    .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON)));

    server
        .expect(
            requestTo(
                "https://eodhd.com/api/eod/EURUSD.FOREX?api_token=test-token&fmt=json&from=2024-01-02&to=2024-01-02"))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 2));

    var amundiValue =
        result.stream().filter(fv -> fv.key().equals("USAS.PA.EODHD")).findFirst().orElseThrow();
    assertThat(amundiValue.key()).isEqualTo("USAS.PA.EODHD");
    assertThat(amundiValue.value()).isEqualByComparingTo(new BigDecimal("4.52"));
  }

  @Test
  void retrievesEurUsdForexRates() {
    var mockForexResponse =
        """
        [
          {"date": "2024-01-02", "open": 1.0950, "high": 1.0970, "low": 1.0940, "close": 1.0960, "adjusted_close": 1.0960, "volume": 0},
          {"date": "2024-01-03", "open": 1.0960, "high": 1.0985, "low": 1.0955, "close": 1.0975, "adjusted_close": 1.0975, "volume": 0},
          {"date": "2024-01-04", "open": 1.0975, "high": 1.0990, "low": 1.0965, "close": 1.0980, "adjusted_close": 1.0980, "volume": 0}
        ]
        """;

    FundTicker.getEodhdTickers()
        .forEach(
            ticker ->
                server
                    .expect(
                        requestTo(
                            expectedUri(
                                ticker, LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 4))))
                    .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON)));

    server
        .expect(
            requestTo(
                "https://eodhd.com/api/eod/EURUSD.FOREX?api_token=test-token&fmt=json&from=2024-01-02&to=2024-01-04"))
        .andRespond(withSuccess(mockForexResponse, MediaType.APPLICATION_JSON));

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 4));

    var forexValues = result.stream().filter(fv -> fv.key().equals("EURUSD.FOREX")).toList();
    assertThat(forexValues).hasSize(3);
    assertThat(forexValues)
        .allSatisfy(
            fv -> {
              assertThat(fv.provider()).isEqualTo("EODHD");
              assertThat(fv.key()).isEqualTo("EURUSD.FOREX");
            });
    assertThat(forexValues)
        .extracting(FundValue::value)
        .containsExactly(
            new BigDecimal("1.0960"), new BigDecimal("1.0975"), new BigDecimal("1.0980"));
  }

  @Test
  void retrievesFundValuesFromEodhdApi() {
    var mockResponse =
        """
        [
          {"date": "2024-01-02", "open": 100.00, "high": 101.00, "low": 99.50, "close": 100.50, "adjusted_close": 100.50, "volume": 12345},
          {"date": "2024-01-03", "open": 100.50, "high": 102.00, "low": 100.00, "close": 101.25, "adjusted_close": 101.25, "volume": 23456},
          {"date": "2024-01-04", "open": 101.25, "high": 103.00, "low": 101.00, "close": 102.00, "adjusted_close": 102.00, "volume": 34567}
        ]
        """;

    FundTicker.getEodhdTickers()
        .forEach(
            ticker ->
                server
                    .expect(
                        requestTo(
                            expectedUri(
                                ticker, LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 4))))
                    .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON)));

    server
        .expect(
            requestTo(
                "https://eodhd.com/api/eod/EURUSD.FOREX?api_token=test-token&fmt=json&from=2024-01-02&to=2024-01-04"))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

    var startDate = LocalDate.of(2024, 1, 2);
    var endDate = LocalDate.of(2024, 1, 4);

    List<FundValue> result = retriever.retrieveValuesForRange(startDate, endDate);

    assertThat(result).hasSize(FundTicker.values().length * 3);
    assertThat(result).allSatisfy(fv -> assertThat(fv.provider()).isEqualTo("EODHD"));
    assertThat(result).allSatisfy(fv -> assertThat(fv.updatedAt()).isNotNull());
  }

  @Test
  void filtersOutZeroValues() {
    var mockResponseWithZeros =
        """
        [
          {"date": "2024-01-02", "open": 100.00, "high": 101.00, "low": 99.50, "close": 100.50, "adjusted_close": 100.50, "volume": 12345},
          {"date": "2024-01-03", "open": 0.0, "high": 0.0, "low": 0.0, "close": 0.0, "adjusted_close": 0.0, "volume": 0},
          {"date": "2024-01-04", "open": 101.25, "high": 103.00, "low": 101.00, "close": 102.00, "adjusted_close": 102.00, "volume": 34567}
        ]
        """;

    FundTicker.getEodhdTickers()
        .forEach(
            ticker ->
                server
                    .expect(
                        requestTo(
                            expectedUri(
                                ticker, LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 4))))
                    .andRespond(withSuccess(mockResponseWithZeros, MediaType.APPLICATION_JSON)));

    server
        .expect(
            requestTo(
                "https://eodhd.com/api/eod/EURUSD.FOREX?api_token=test-token&fmt=json&from=2024-01-02&to=2024-01-04"))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

    var startDate = LocalDate.of(2024, 1, 2);
    var endDate = LocalDate.of(2024, 1, 4);

    List<FundValue> result = retriever.retrieveValuesForRange(startDate, endDate);

    assertThat(result).hasSize(FundTicker.values().length * 2);
    assertThat(result).allSatisfy(fv -> assertThat(fv.value()).isNotEqualTo(ZERO));
  }

  @Test
  void parsesAdjustedCloseValuesCorrectly() {
    var mockResponse =
        """
        [
          {"date": "2024-01-02", "open": 123.00, "high": 124.00, "low": 122.00, "close": 123.456789, "adjusted_close": 123.456789, "volume": 12345}
        ]
        """;

    var firstTicker = FundTicker.getEodhdTickers().getFirst();
    server
        .expect(
            requestTo(expectedUri(firstTicker, LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 2))))
        .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

    FundTicker.getEodhdTickers().stream()
        .skip(1)
        .forEach(
            ticker ->
                server
                    .expect(
                        requestTo(
                            expectedUri(
                                ticker, LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 2))))
                    .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON)));

    server
        .expect(
            requestTo(
                "https://eodhd.com/api/eod/EURUSD.FOREX?api_token=test-token&fmt=json&from=2024-01-02&to=2024-01-02"))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

    var startDate = LocalDate.of(2024, 1, 2);
    var endDate = LocalDate.of(2024, 1, 2);

    List<FundValue> result = retriever.retrieveValuesForRange(startDate, endDate);

    var firstFundValue =
        result.stream().filter(fv -> fv.key().equals(firstTicker)).findFirst().orElseThrow();
    assertThat(firstFundValue.value()).isEqualByComparingTo(new BigDecimal("123.456789"));
    assertThat(firstFundValue.date()).isEqualTo(LocalDate.of(2024, 1, 2));
  }

  @Test
  void returnsEmptyListOnApiError() {
    FundTicker.getEodhdTickers()
        .forEach(
            ticker ->
                server
                    .expect(
                        requestTo(
                            expectedUri(
                                ticker, LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 4))))
                    .andRespond(withServerError()));

    server
        .expect(
            requestTo(
                "https://eodhd.com/api/eod/EURUSD.FOREX?api_token=test-token&fmt=json&from=2024-01-02&to=2024-01-04"))
        .andRespond(withServerError());

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 4));

    assertThat(result).isEmpty();
  }

  @Test
  void alwaysExcludesTodaysData() {
    // 2024-01-04 20:00 UTC = 21:00 CET (well after any market close)
    ClockHolder.setClock(Clock.fixed(Instant.parse("2024-01-04T20:00:00Z"), UTC));

    var mockResponse =
        """
        [
          {"date": "2024-01-02", "open": 100.00, "high": 101.00, "low": 99.50, "close": 100.50, "adjusted_close": 100.50, "volume": 12345},
          {"date": "2024-01-03", "open": 100.50, "high": 102.00, "low": 100.00, "close": 101.25, "adjusted_close": 101.25, "volume": 23456},
          {"date": "2024-01-04", "open": 101.25, "high": 103.00, "low": 101.00, "close": 102.00, "adjusted_close": 102.00, "volume": 34567}
        ]
        """;

    FundTicker.getEodhdTickers()
        .forEach(
            ticker ->
                server
                    .expect(
                        requestTo(
                            expectedUri(
                                ticker, LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 4))))
                    .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON)));

    server
        .expect(
            requestTo(
                "https://eodhd.com/api/eod/EURUSD.FOREX?api_token=test-token&fmt=json&from=2024-01-02&to=2024-01-04"))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

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
  void dropsCarriedForwardClosesForEuronextParisTickers() {
    // 2026-08-20 12:00 UTC = 14:00 CET, so the cutoff is 2026-08-19
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-08-20T12:00:00Z"), UTC));

    var mockResponse =
        """
        [
          {"date": "2026-08-17", "open": 5.073, "high": 5.073, "low": 5.06, "close": 5.06, "adjusted_close": 5.063, "volume": 1035},
          {"date": "2026-08-18", "open": 5.027, "high": 5.027, "low": 5.02, "close": 5.02, "adjusted_close": 5.007, "volume": 1042},
          {"date": "2026-08-19", "open": 5.007, "high": 5.007, "low": 5.007, "close": 5.007, "adjusted_close": 5.007, "volume": 1049}
        ]
        """;

    given(fundValueProvider.getLatestValue("IE000F60HVH9.XPAR", LocalDate.of(2026, 8, 19)))
        .willReturn(
            Optional.of(
                new FundValue(
                    "IE000F60HVH9.XPAR",
                    LocalDate.of(2026, 8, 19),
                    new BigDecimal("4.993"),
                    "EURONEXT",
                    null)));

    expectRequests(
        "USAS.PA.EODHD", mockResponse, LocalDate.of(2026, 8, 19), LocalDate.of(2026, 8, 19));

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2026, 8, 19), LocalDate.of(2026, 8, 19));

    assertThat(result).noneMatch(fv -> fv.key().equals("USAS.PA.EODHD"));
  }

  @Test
  void keepsRepeatedCloseConfirmedByEuronext() {
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-08-20T12:00:00Z"), UTC));

    var mockResponse =
        """
        [
          {"date": "2026-08-18", "open": 5.027, "high": 5.027, "low": 5.02, "close": 5.02, "adjusted_close": 5.007, "volume": 1042},
          {"date": "2026-08-19", "open": 5.007, "high": 5.007, "low": 5.007, "close": 5.007, "adjusted_close": 5.007, "volume": 1049}
        ]
        """;

    given(fundValueProvider.getLatestValue("IE000F60HVH9.XPAR", LocalDate.of(2026, 8, 19)))
        .willReturn(
            Optional.of(
                new FundValue(
                    "IE000F60HVH9.XPAR",
                    LocalDate.of(2026, 8, 19),
                    new BigDecimal("5.007"),
                    "EURONEXT",
                    null)));

    expectRequests(
        "USAS.PA.EODHD", mockResponse, LocalDate.of(2026, 8, 19), LocalDate.of(2026, 8, 19));

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2026, 8, 19), LocalDate.of(2026, 8, 19));

    var parisValues = result.stream().filter(fv -> fv.key().equals("USAS.PA.EODHD")).toList();
    assertThat(parisValues)
        .extracting(FundValue::date, FundValue::value)
        .containsExactly(tuple(LocalDate.of(2026, 8, 19), new BigDecimal("5.007")));
  }

  @Test
  void dropsRepeatedCloseWhenEuronextHasNoSameDateValue() {
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-08-20T12:00:00Z"), UTC));

    var mockResponse =
        """
        [
          {"date": "2026-08-18", "open": 5.027, "high": 5.027, "low": 5.02, "close": 5.02, "adjusted_close": 5.007, "volume": 1042},
          {"date": "2026-08-19", "open": 5.007, "high": 5.007, "low": 5.007, "close": 5.007, "adjusted_close": 5.007, "volume": 1049}
        ]
        """;

    given(fundValueProvider.getLatestValue("IE000F60HVH9.XPAR", LocalDate.of(2026, 8, 19)))
        .willReturn(
            Optional.of(
                new FundValue(
                    "IE000F60HVH9.XPAR",
                    LocalDate.of(2026, 8, 18),
                    new BigDecimal("5.007"),
                    "EURONEXT",
                    null)));

    expectRequests(
        "USAS.PA.EODHD", mockResponse, LocalDate.of(2026, 8, 19), LocalDate.of(2026, 8, 19));

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2026, 8, 19), LocalDate.of(2026, 8, 19));

    assertThat(result).noneMatch(fv -> fv.key().equals("USAS.PA.EODHD"));
  }

  @Test
  void keepsChangedClosesForEuronextParisTickers() {
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-08-20T12:00:00Z"), UTC));

    var mockResponse =
        """
        [
          {"date": "2026-08-17", "open": 5.073, "high": 5.073, "low": 5.06, "close": 5.06, "adjusted_close": 5.063, "volume": 1035},
          {"date": "2026-08-18", "open": 5.027, "high": 5.027, "low": 5.02, "close": 5.02, "adjusted_close": 5.007, "volume": 1042},
          {"date": "2026-08-19", "open": 4.9975, "high": 5.00, "low": 4.9975, "close": 5.00, "adjusted_close": 4.993, "volume": 1049}
        ]
        """;

    expectRequests(
        "USAS.PA.EODHD", mockResponse, LocalDate.of(2026, 8, 19), LocalDate.of(2026, 8, 19));

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2026, 8, 19), LocalDate.of(2026, 8, 19));

    var parisValues = result.stream().filter(fv -> fv.key().equals("USAS.PA.EODHD")).toList();
    assertThat(parisValues)
        .extracting(FundValue::date, FundValue::value)
        .containsExactly(tuple(LocalDate.of(2026, 8, 19), new BigDecimal("4.993")));
  }

  @Test
  void dropsConsecutiveCarriedForwardClosesForEuronextParisTickers() {
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-08-20T12:00:00Z"), UTC));

    var mockResponse =
        """
        [
          {"date": "2026-08-17", "open": 48.875, "high": 48.875, "low": 48.875, "close": 48.875, "adjusted_close": 48.875, "volume": 28},
          {"date": "2026-08-18", "open": 48.875, "high": 48.875, "low": 48.875, "close": 48.875, "adjusted_close": 48.875, "volume": 29},
          {"date": "2026-08-19", "open": 48.875, "high": 48.875, "low": 48.875, "close": 48.875, "adjusted_close": 48.875, "volume": 2660}
        ]
        """;

    given(fundValueProvider.getLatestValue("LU1708330318.XPAR", LocalDate.of(2026, 8, 18)))
        .willReturn(
            Optional.of(
                new FundValue(
                    "LU1708330318.XPAR",
                    LocalDate.of(2026, 8, 18),
                    new BigDecimal("48.795"),
                    "EURONEXT",
                    null)));
    given(fundValueProvider.getLatestValue("LU1708330318.XPAR", LocalDate.of(2026, 8, 19)))
        .willReturn(
            Optional.of(
                new FundValue(
                    "LU1708330318.XPAR",
                    LocalDate.of(2026, 8, 19),
                    new BigDecimal("48.89"),
                    "EURONEXT",
                    null)));

    expectRequests(
        "GAGH.PA.EODHD", mockResponse, LocalDate.of(2026, 8, 18), LocalDate.of(2026, 8, 19));

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2026, 8, 18), LocalDate.of(2026, 8, 19));

    assertThat(result).noneMatch(fv -> fv.key().equals("GAGH.PA.EODHD"));
  }

  @Test
  void keepsRepeatedClosesForNonParisTickers() {
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-08-20T12:00:00Z"), UTC));

    var mockResponse =
        """
        [
          {"date": "2026-08-18", "open": 13.85, "high": 13.85, "low": 13.84, "close": 13.844, "adjusted_close": 13.844, "volume": 280524},
          {"date": "2026-08-19", "open": 13.84, "high": 13.85, "low": 13.84, "close": 13.844, "adjusted_close": 13.844, "volume": 309691}
        ]
        """;

    expectRequests(
        "SGAS.XETRA", mockResponse, LocalDate.of(2026, 8, 18), LocalDate.of(2026, 8, 19));

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2026, 8, 18), LocalDate.of(2026, 8, 19));

    var xetraValues = result.stream().filter(fv -> fv.key().equals("SGAS.XETRA")).toList();
    assertThat(xetraValues)
        .extracting(FundValue::date)
        .containsExactly(LocalDate.of(2026, 8, 18), LocalDate.of(2026, 8, 19));
  }

  private void expectRequests(
      String targetTicker, String targetResponse, LocalDate startDate, LocalDate endDate) {
    FundTicker.getEodhdTickers()
        .forEach(
            ticker ->
                server
                    .expect(requestTo(expectedUri(ticker, startDate, endDate)))
                    .andRespond(
                        withSuccess(
                            ticker.equals(targetTicker) ? targetResponse : "[]",
                            MediaType.APPLICATION_JSON)));

    server
        .expect(requestTo(expectedUri("EURUSD.FOREX", startDate, endDate)))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
  }

  private String expectedUri(String storageTicker, LocalDate startDate, LocalDate endDate) {
    return "https://eodhd.com/api/eod/"
        + expectedApiTicker(storageTicker)
        + "?api_token=test-token&fmt=json&from="
        + expectedFromDate(storageTicker, startDate)
        + "&to="
        + endDate;
  }

  private LocalDate expectedFromDate(String storageTicker, LocalDate startDate) {
    return storageTicker.endsWith(".PA.EODHD") ? startDate.minusDays(7) : startDate;
  }

  private String expectedApiTicker(String storageTicker) {
    return storageTicker.endsWith(".EODHD")
        ? storageTicker.substring(0, storageTicker.lastIndexOf("."))
        : storageTicker;
  }
}
