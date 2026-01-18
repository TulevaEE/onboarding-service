package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;

@RestClientTest(EODHDValueRetriever.class)
@TestPropertySource(properties = "eodhd.api-token=test-token")
class EODHDValueRetrieverTest {

  @Autowired EODHDValueRetriever retriever;

  @Autowired MockRestServiceServer server;

  @AfterEach
  void cleanup() {
    server.reset();
  }

  @Test
  void returnsCorrectKey() {
    assertThat(retriever.getKey()).isEqualTo("EODHD_VALUE");
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
                            "https://eodhd.com/api/eod/"
                                + expectedApiTicker(ticker)
                                + "?api_token=test-token&fmt=json&from=2024-01-02&to=2024-01-02"))
                    .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON)));

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 2));

    var amundiValue =
        result.stream().filter(fv -> fv.key().equals("USAS.PA.EODHD")).findFirst().orElseThrow();
    assertThat(amundiValue.key()).isEqualTo("USAS.PA.EODHD");
    assertThat(amundiValue.value()).isEqualByComparingTo(new BigDecimal("4.52"));
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
                            "https://eodhd.com/api/eod/"
                                + expectedApiTicker(ticker)
                                + "?api_token=test-token&fmt=json&from=2024-01-02&to=2024-01-04"))
                    .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON)));

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
                            "https://eodhd.com/api/eod/"
                                + expectedApiTicker(ticker)
                                + "?api_token=test-token&fmt=json&from=2024-01-02&to=2024-01-04"))
                    .andRespond(withSuccess(mockResponseWithZeros, MediaType.APPLICATION_JSON)));

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
            requestTo(
                "https://eodhd.com/api/eod/"
                    + expectedApiTicker(firstTicker)
                    + "?api_token=test-token&fmt=json&from=2024-01-02&to=2024-01-02"))
        .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

    FundTicker.getEodhdTickers().stream()
        .skip(1)
        .forEach(
            ticker ->
                server
                    .expect(
                        requestTo(
                            "https://eodhd.com/api/eod/"
                                + expectedApiTicker(ticker)
                                + "?api_token=test-token&fmt=json&from=2024-01-02&to=2024-01-02"))
                    .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON)));

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
                            "https://eodhd.com/api/eod/"
                                + expectedApiTicker(ticker)
                                + "?api_token=test-token&fmt=json&from=2024-01-02&to=2024-01-04"))
                    .andRespond(withServerError()));

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 4));

    assertThat(result).isEmpty();
  }

  private String expectedApiTicker(String storageTicker) {
    return storageTicker.equals("USAS.PA.EODHD") ? "USAS.PA" : storageTicker;
  }
}
