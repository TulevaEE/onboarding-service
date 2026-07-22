package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import static java.math.BigDecimal.ZERO;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.time.ClockConfig;
import ee.tuleva.onboarding.time.ClockHolder;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.client.MockRestServiceServer;

@RestClientTest(DeutscheBoerseValueRetriever.class)
@Import(ClockConfig.class)
class DeutscheBoerseValueRetrieverTest {

  @Autowired DeutscheBoerseValueRetriever retriever;

  @Autowired MockRestServiceServer server;

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
            FundTicker.getXetraIsins().stream().map(isin -> isin + ".XETR").toList());
  }

  @Test
  void retrievesFundValuesFromDeutscheBoerseApi() {
    FundTicker.getXetraIsins()
        .forEach(
            isin -> {
              server
                  .expect(requestTo(priceHistoryUrl(isin, "2024-01-02", "2024-01-04")))
                  .andRespond(
                      withSuccess(
                          mockResponseForIsin(isin, "2024-01-02", "2024-01-04"), APPLICATION_JSON));
              server
                  .expect(requestTo(quoteBoxUrl(isin)))
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
    var isin = FundTicker.getXetraIsins().getFirst();
    var mockResponse =
        """
        {
          "isin": "%s",
          "data": [
            {"date": "2024-01-02", "open": 100.00, "close": 102.82, "high": 103.00, "low": 102.32, "turnoverPieces": 1920, "turnoverEuro": 197186.02}
          ],
          "totalCount": 1
        }
        """
            .formatted(isin);

    server
        .expect(
            requestTo(
                "https://mobile-api.live.deutsche-boerse.com/v1/data/price_history"
                    + "?isin="
                    + isin
                    + "&mic=XETR&minDate=2024-01-02&maxDate=2024-01-02"))
        .andRespond(withSuccess(mockResponse, APPLICATION_JSON));
    server.expect(requestTo(quoteBoxUrl(isin))).andRespond(withSuccess("{}", APPLICATION_JSON));

    FundTicker.getXetraIsins().stream()
        .skip(1)
        .forEach(
            otherIsin -> {
              server
                  .expect(requestTo(priceHistoryUrl(otherIsin, "2024-01-02", "2024-01-02")))
                  .andRespond(withSuccess(emptyResponse(otherIsin), APPLICATION_JSON));
              server
                  .expect(requestTo(quoteBoxUrl(otherIsin)))
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
    FundTicker.getXetraIsins()
        .forEach(
            isin -> {
              server
                  .expect(requestTo(priceHistoryUrl(isin, "2024-01-02", "2024-01-04")))
                  .andRespond(withSuccess(mockResponseWithZero(isin), APPLICATION_JSON));
              server
                  .expect(requestTo(quoteBoxUrl(isin)))
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
    FundTicker.getXetraIsins()
        .forEach(
            isin ->
                server
                    .expect(
                        requestTo(
                            "https://mobile-api.live.deutsche-boerse.com/v1/data/price_history"
                                + "?isin="
                                + isin
                                + "&mic=XETR&minDate=2024-01-02&maxDate=2024-01-04"))
                    .andRespond(withServerError()));

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 4));

    assertThat(result).isEmpty();
  }

  @Test
  void handlesEmptyDataResponse() {
    FundTicker.getXetraIsins()
        .forEach(
            isin -> {
              server
                  .expect(requestTo(priceHistoryUrl(isin, "2024-01-02", "2024-01-02")))
                  .andRespond(withSuccess(emptyResponse(isin), APPLICATION_JSON));
              server
                  .expect(requestTo(quoteBoxUrl(isin)))
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

    FundTicker.getXetraIsins()
        .forEach(
            isin -> {
              server
                  .expect(requestTo(priceHistoryUrl(isin, "2024-01-02", "2024-01-04")))
                  .andRespond(
                      withSuccess(
                          mockResponseForIsin(isin, "2024-01-02", "2024-01-04"), APPLICATION_JSON));
              server
                  .expect(requestTo(quoteBoxUrl(isin)))
                  .andRespond(
                      withSuccess(
                          quoteBoxResponse(isin, "102.00", "2024-01-04T16:35:00Z"),
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

    FundTicker.getXetraIsins()
        .forEach(
            isin -> {
              server
                  .expect(requestTo(priceHistoryUrl(isin, "2024-01-02", "2024-01-04")))
                  .andRespond(
                      withSuccess(
                          mockResponseForIsin(isin, "2024-01-02", "2024-01-04"), APPLICATION_JSON));
              server
                  .expect(requestTo(quoteBoxUrl(isin)))
                  .andRespond(
                      withSuccess(
                          quoteBoxResponse(isin, "102.00", "2024-01-04T16:35:00Z"),
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

    FundTicker.getXetraIsins()
        .forEach(
            isin -> {
              server
                  .expect(requestTo(priceHistoryUrl(isin, "2024-01-02", "2024-01-04")))
                  .andRespond(
                      withSuccess(
                          mockResponseForIsin(isin, "2024-01-02", "2024-01-04"), APPLICATION_JSON));
              server
                  .expect(requestTo(quoteBoxUrl(isin)))
                  .andRespond(
                      withSuccess(
                          quoteBoxResponse(isin, "102.00", "2024-01-04T16:35:00Z"),
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
    var isin = FundTicker.getXetraIsins().getFirst();
    var thinDayBars =
        """
        {
          "isin": "%s",
          "data": [
            {"date": "2026-07-17", "open": 9.900, "close": 9.92, "high": 9.924, "low": 9.900, "turnoverPieces": 0, "turnoverEuro": 0.0},
            {"date": "2026-07-20", "open": 9.905, "close": 9.969, "high": 9.969, "low": 9.905, "turnoverPieces": 10, "turnoverEuro": 99.69}
          ],
          "totalCount": 2
        }
        """
            .formatted(isin);

    server
        .expect(requestTo(priceHistoryUrl(isin, "2026-07-17", "2026-07-20")))
        .andRespond(withSuccess(thinDayBars, APPLICATION_JSON));
    server
        .expect(requestTo(quoteBoxUrl(isin)))
        .andRespond(
            withSuccess(quoteBoxResponse(isin, "9.947", "2026-07-20T15:35:48Z"), APPLICATION_JSON));
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
    // 2026-07-21 08:00 UTC = 10:00 CEST, Xetra is open and quote box serves a live price
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-07-21T08:00:00Z"), UTC));
    var isin = FundTicker.getXetraIsins().getFirst();
    var bars =
        """
        {
          "isin": "%s",
          "data": [
            {"date": "2026-07-20", "open": 9.905, "close": 9.969, "high": 9.969, "low": 9.905, "turnoverPieces": 10, "turnoverEuro": 99.69}
          ],
          "totalCount": 1
        }
        """
            .formatted(isin);

    server
        .expect(requestTo(priceHistoryUrl(isin, "2026-07-17", "2026-07-21")))
        .andRespond(withSuccess(bars, APPLICATION_JSON));
    server
        .expect(requestTo(quoteBoxUrl(isin)))
        .andRespond(
            withSuccess(quoteBoxResponse(isin, "9.943", "2026-07-21T07:16:42Z"), APPLICATION_JSON));
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
    // 2026-07-22 04:00 UTC = 06:00 CEST: 2026-07-21 is finalized, quote box still shows a
    // retail session print from 20:05 CEST the previous evening
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-07-22T04:00:00Z"), UTC));
    var isin = FundTicker.getXetraIsins().getFirst();
    var bars =
        """
        {
          "isin": "%s",
          "data": [
            {"date": "2026-07-20", "open": 8.701, "close": 8.81, "high": 8.849, "low": 8.69, "turnoverPieces": 176115, "turnoverEuro": 1543846.72},
            {"date": "2026-07-21", "open": 8.944, "close": 9.002, "high": 9.002, "low": 8.927, "turnoverPieces": 135778, "turnoverEuro": 1215393.01}
          ],
          "totalCount": 2
        }
        """
            .formatted(isin);

    server
        .expect(requestTo(priceHistoryUrl(isin, "2026-07-20", "2026-07-21")))
        .andRespond(withSuccess(bars, APPLICATION_JSON));
    server
        .expect(requestTo(quoteBoxUrl(isin)))
        .andRespond(
            withSuccess(
                quoteBoxResponse(isin, "9.045", "2026-07-21T18:05:14Z", "R"), APPLICATION_JSON));
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
  void doesNotHoldBackBarCloseWhenQuoteBoxServesRetailPrintFromToday() {
    // 2026-07-22 06:30 UTC = 08:30 CEST: the retail early session prints before Xetra opens
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-07-22T06:30:00Z"), UTC));
    var isin = FundTicker.getXetraIsins().getFirst();
    var bars =
        """
        {
          "isin": "%s",
          "data": [
            {"date": "2026-07-20", "open": 8.701, "close": 8.81, "high": 8.849, "low": 8.69, "turnoverPieces": 176115, "turnoverEuro": 1543846.72},
            {"date": "2026-07-21", "open": 8.944, "close": 9.002, "high": 9.002, "low": 8.927, "turnoverPieces": 135778, "turnoverEuro": 1215393.01}
          ],
          "totalCount": 2
        }
        """
            .formatted(isin);

    server
        .expect(requestTo(priceHistoryUrl(isin, "2026-07-20", "2026-07-21")))
        .andRespond(withSuccess(bars, APPLICATION_JSON));
    server
        .expect(requestTo(quoteBoxUrl(isin)))
        .andRespond(
            withSuccess(
                quoteBoxResponse(isin, "9.058", "2026-07-22T06:01:29Z", "R"), APPLICATION_JSON));
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
  void holdsBackLatestFinalizedDayWhenOfficialLastPriceUnavailable() {
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-07-21T05:00:00Z"), UTC));
    var isin = FundTicker.getXetraIsins().getFirst();
    var bars =
        """
        {
          "isin": "%s",
          "data": [
            {"date": "2026-07-17", "open": 9.900, "close": 9.92, "high": 9.924, "low": 9.900, "turnoverPieces": 0, "turnoverEuro": 0.0},
            {"date": "2026-07-20", "open": 9.905, "close": 9.969, "high": 9.969, "low": 9.905, "turnoverPieces": 10, "turnoverEuro": 99.69}
          ],
          "totalCount": 2
        }
        """
            .formatted(isin);

    server
        .expect(requestTo(priceHistoryUrl(isin, "2026-07-17", "2026-07-20")))
        .andRespond(withSuccess(bars, APPLICATION_JSON));
    server.expect(requestTo(quoteBoxUrl(isin))).andRespond(withServerError());
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
  void holdsBackLatestFinalizedDayWhenQuoteBoxHasNoUsableLastPrice() {
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-07-21T05:00:00Z"), UTC));
    var isin = FundTicker.getXetraIsins().getFirst();
    var bars =
        """
        {
          "isin": "%s",
          "data": [
            {"date": "2026-07-17", "open": 9.900, "close": 9.92, "high": 9.924, "low": 9.900, "turnoverPieces": 0, "turnoverEuro": 0.0},
            {"date": "2026-07-20", "open": 9.905, "close": 9.969, "high": 9.969, "low": 9.905, "turnoverPieces": 10, "turnoverEuro": 99.69}
          ],
          "totalCount": 2
        }
        """
            .formatted(isin);

    server
        .expect(requestTo(priceHistoryUrl(isin, "2026-07-17", "2026-07-20")))
        .andRespond(withSuccess(bars, APPLICATION_JSON));
    server.expect(requestTo(quoteBoxUrl(isin))).andRespond(withSuccess("{}", APPLICATION_JSON));
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
  void insertsOlderBarValuesEvenWhenOfficialLastPriceUnavailable() {
    // A day later the unconfirmed 2026-07-20 close is no longer the latest finalized day
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-07-22T05:00:00Z"), UTC));
    var isin = FundTicker.getXetraIsins().getFirst();
    var bars =
        """
        {
          "isin": "%s",
          "data": [
            {"date": "2026-07-20", "open": 9.905, "close": 9.969, "high": 9.969, "low": 9.905, "turnoverPieces": 10, "turnoverEuro": 99.69},
            {"date": "2026-07-21", "open": 9.943, "close": 9.951, "high": 9.955, "low": 9.940, "turnoverPieces": 500, "turnoverEuro": 4975.50}
          ],
          "totalCount": 2
        }
        """
            .formatted(isin);

    server
        .expect(requestTo(priceHistoryUrl(isin, "2026-07-20", "2026-07-21")))
        .andRespond(withSuccess(bars, APPLICATION_JSON));
    server.expect(requestTo(quoteBoxUrl(isin))).andRespond(withServerError());
    expectNoDataForOtherIsins(isin, "2026-07-20", "2026-07-21");

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 21));

    assertThat(result.stream().filter(value -> value.key().equals(isin + ".XETR")))
        .containsExactlyInAnyOrder(
            new FundValue(
                isin + ".XETR",
                LocalDate.of(2026, 7, 20),
                new BigDecimal("9.969"),
                "DEUTSCHE_BOERSE",
                Instant.parse("2026-07-22T05:00:00Z")));
  }

  @Test
  void usesOfficialLastPriceWhenPriceHistoryReturnsNoData() {
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-07-21T05:00:00Z"), UTC));
    var isin = FundTicker.getXetraIsins().getFirst();

    server
        .expect(requestTo(priceHistoryUrl(isin, "2026-07-17", "2026-07-20")))
        .andRespond(withSuccess(emptyResponse(isin), APPLICATION_JSON));
    server
        .expect(requestTo(quoteBoxUrl(isin)))
        .andRespond(
            withSuccess(quoteBoxResponse(isin, "9.947", "2026-07-20T15:35:48Z"), APPLICATION_JSON));
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

  private void expectNoDataForOtherIsins(String isin, String startDate, String endDate) {
    FundTicker.getXetraIsins().stream()
        .filter(otherIsin -> !otherIsin.equals(isin))
        .forEach(
            otherIsin -> {
              server
                  .expect(requestTo(priceHistoryUrl(otherIsin, startDate, endDate)))
                  .andRespond(withSuccess(emptyResponse(otherIsin), APPLICATION_JSON));
              server
                  .expect(requestTo(quoteBoxUrl(otherIsin)))
                  .andRespond(withSuccess("{}", APPLICATION_JSON));
            });
  }

  private String priceHistoryUrl(String isin, String startDate, String endDate) {
    return "https://mobile-api.live.deutsche-boerse.com/v1/data/price_history?isin="
        + isin
        + "&mic=XETR&minDate="
        + startDate
        + "&maxDate="
        + endDate;
  }

  private String quoteBoxUrl(String isin) {
    return "https://api.live.deutsche-boerse.com/v1/data/quote_box/single?isin="
        + isin
        + "&mic=XETR";
  }

  private String quoteBoxResponse(String isin, String lastPrice, String timestampLastPrice) {
    return """
        {
          "isin": "%s",
          "lastPrice": %s,
          "timestampLastPrice": "%s",
          "instrumentStatus": "Active",
          "tradingStatus": "Continuous Trading"
        }
        """
        .formatted(isin, lastPrice, timestampLastPrice);
  }

  private String quoteBoxResponse(
      String isin, String lastPrice, String timestampLastPrice, String lastPriceIndicator) {
    return """
        {
          "isin": "%s",
          "lastPrice": %s,
          "timestampLastPrice": "%s",
          "lastPriceIndicator": "%s",
          "instrumentStatus": "Active",
          "tradingStatus": "Retail Early/Late"
        }
        """
        .formatted(isin, lastPrice, timestampLastPrice, lastPriceIndicator);
  }

  private String mockResponseForIsin(String isin, String startDate, String endDate) {
    return """
        {
          "isin": "%s",
          "data": [
            {"date": "%s", "open": 100.00, "close": 100.50, "high": 101.00, "low": 99.50, "turnoverPieces": 1000, "turnoverEuro": 100500.00},
            {"date": "2024-01-03", "open": 100.50, "close": 101.25, "high": 102.00, "low": 100.00, "turnoverPieces": 2000, "turnoverEuro": 202500.00},
            {"date": "%s", "open": 101.25, "close": 102.00, "high": 103.00, "low": 101.00, "turnoverPieces": 3000, "turnoverEuro": 306000.00}
          ],
          "totalCount": 3
        }
        """
        .formatted(isin, startDate, endDate);
  }

  private String mockResponseWithZero(String isin) {
    return """
        {
          "isin": "%s",
          "data": [
            {"date": "2024-01-02", "open": 100.00, "close": 100.50, "high": 101.00, "low": 99.50, "turnoverPieces": 1000, "turnoverEuro": 100500.00},
            {"date": "2024-01-03", "open": 0.0, "close": 0.0, "high": 0.0, "low": 0.0, "turnoverPieces": 0, "turnoverEuro": 0.0},
            {"date": "2024-01-04", "open": 101.25, "close": 102.00, "high": 103.00, "low": 101.00, "turnoverPieces": 3000, "turnoverEuro": 306000.00}
          ],
          "totalCount": 3
        }
        """
        .formatted(isin);
  }

  private String emptyResponse(String isin) {
    return """
        {
          "isin": "%s",
          "data": [],
          "totalCount": 0
        }
        """
        .formatted(isin);
  }
}
