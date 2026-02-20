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
  void retrievesFundValuesFromDeutscheBoerseApi() {
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
                    .andRespond(
                        withSuccess(
                            mockResponseForIsin(isin, "2024-01-02", "2024-01-04"),
                            APPLICATION_JSON)));

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

    FundTicker.getXetraIsins().stream()
        .skip(1)
        .forEach(
            otherIsin ->
                server
                    .expect(
                        requestTo(
                            "https://mobile-api.live.deutsche-boerse.com/v1/data/price_history"
                                + "?isin="
                                + otherIsin
                                + "&mic=XETR&minDate=2024-01-02&maxDate=2024-01-02"))
                    .andRespond(withSuccess(emptyResponse(otherIsin), APPLICATION_JSON)));

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
            isin ->
                server
                    .expect(
                        requestTo(
                            "https://mobile-api.live.deutsche-boerse.com/v1/data/price_history"
                                + "?isin="
                                + isin
                                + "&mic=XETR&minDate=2024-01-02&maxDate=2024-01-04"))
                    .andRespond(withSuccess(mockResponseWithZero(isin), APPLICATION_JSON)));

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
            isin ->
                server
                    .expect(
                        requestTo(
                            "https://mobile-api.live.deutsche-boerse.com/v1/data/price_history"
                                + "?isin="
                                + isin
                                + "&mic=XETR&minDate=2024-01-02&maxDate=2024-01-02"))
                    .andRespond(withSuccess(emptyResponse(isin), APPLICATION_JSON)));

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
            isin ->
                server
                    .expect(
                        requestTo(
                            "https://mobile-api.live.deutsche-boerse.com/v1/data/price_history"
                                + "?isin="
                                + isin
                                + "&mic=XETR&minDate=2024-01-02&maxDate=2024-01-04"))
                    .andRespond(
                        withSuccess(
                            mockResponseForIsin(isin, "2024-01-02", "2024-01-04"),
                            APPLICATION_JSON)));

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
            isin ->
                server
                    .expect(
                        requestTo(
                            "https://mobile-api.live.deutsche-boerse.com/v1/data/price_history"
                                + "?isin="
                                + isin
                                + "&mic=XETR&minDate=2024-01-02&maxDate=2024-01-04"))
                    .andRespond(
                        withSuccess(
                            mockResponseForIsin(isin, "2024-01-02", "2024-01-04"),
                            APPLICATION_JSON)));

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
            isin ->
                server
                    .expect(
                        requestTo(
                            "https://mobile-api.live.deutsche-boerse.com/v1/data/price_history"
                                + "?isin="
                                + isin
                                + "&mic=XETR&minDate=2024-01-02&maxDate=2024-01-04"))
                    .andRespond(
                        withSuccess(
                            mockResponseForIsin(isin, "2024-01-02", "2024-01-04"),
                            APPLICATION_JSON)));

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
