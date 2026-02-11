package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import static java.math.BigDecimal.ZERO;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.TEXT_PLAIN;
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
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.client.MockRestServiceServer;

@RestClientTest(EuronextValueRetriever.class)
@Import(ClockConfig.class)
class EuronextValueRetrieverTest {

  @Autowired EuronextValueRetriever retriever;

  @Autowired MockRestServiceServer server;

  @AfterEach
  void cleanup() {
    server.reset();
    ClockHolder.setDefaultClock();
  }

  @Test
  void returnsCorrectKey() {
    assertThat(retriever.getKey()).isEqualTo("EURONEXT_VALUE");
  }

  @Test
  void retrievesFundValuesFromEuronextApi() {
    FundTicker.getEuronextParisIsins()
        .forEach(
            isin ->
                server
                    .expect(
                        requestTo(
                            "https://live.euronext.com/en/ajax/AwlHistoricalPrice/getFullDownloadAjax/"
                                + isin
                                + "-XPAR?format=csv&decimal_separator=.&date_form=d/m/Y&adjusted=Y&startdate=2024-01-02&enddate=2024-01-04"))
                    .andRespond(withSuccess(mockCsvResponseForIsin(isin), TEXT_PLAIN)));

    var startDate = LocalDate.of(2024, 1, 2);
    var endDate = LocalDate.of(2024, 1, 4);

    List<FundValue> result = retriever.retrieveValuesForRange(startDate, endDate);

    assertThat(result)
        .isNotEmpty()
        .allSatisfy(
            fundValue -> {
              assertThat(fundValue.provider()).isEqualTo("EURONEXT");
              assertThat(fundValue.updatedAt()).isNotNull();
              assertThat(fundValue.key()).endsWith(".XPAR");
            });
  }

  @Test
  void usesIsinAsKeyWithXparSuffix() {
    var isin = FundTicker.getEuronextParisIsins().getFirst();
    var mockResponse =
        """
        "Historical Data"
        "From 2024-01-02 to 2024-01-02"
        %s
        Date;Open;High;Low;Last;Close;Number of Shares;Number of Trades;Turnover
        02/01/2024;4.506;4.506;4.4925;4.497;4.495;3370;6;15147;4.494641
        """
            .formatted(isin);

    server
        .expect(
            requestTo(
                "https://live.euronext.com/en/ajax/AwlHistoricalPrice/getFullDownloadAjax/"
                    + isin
                    + "-XPAR?format=csv&decimal_separator=.&date_form=d/m/Y&adjusted=Y&startdate=2024-01-02&enddate=2024-01-02"))
        .andRespond(withSuccess(mockResponse, TEXT_PLAIN));

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 2));

    var fundValue =
        result.stream()
            .filter(value -> value.key().equals(isin + ".XPAR"))
            .findFirst()
            .orElseThrow();
    assertThat(fundValue.key()).isEqualTo(isin + ".XPAR");
    assertThat(fundValue.value()).isEqualByComparingTo(new BigDecimal("4.495"));
    assertThat(fundValue.date()).isEqualTo(LocalDate.of(2024, 1, 2));
  }

  @Test
  void filtersOutZeroValues() {
    FundTicker.getEuronextParisIsins()
        .forEach(
            isin ->
                server
                    .expect(
                        requestTo(
                            "https://live.euronext.com/en/ajax/AwlHistoricalPrice/getFullDownloadAjax/"
                                + isin
                                + "-XPAR?format=csv&decimal_separator=.&date_form=d/m/Y&adjusted=Y&startdate=2024-01-02&enddate=2024-01-04"))
                    .andRespond(withSuccess(mockCsvResponseWithZero(isin), TEXT_PLAIN)));

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 4));

    assertThat(result)
        .isNotEmpty()
        .allSatisfy(fundValue -> assertThat(fundValue.value()).isNotEqualTo(ZERO));
  }

  @Test
  void returnsEmptyListOnApiError() {
    FundTicker.getEuronextParisIsins()
        .forEach(
            isin ->
                server
                    .expect(
                        requestTo(
                            "https://live.euronext.com/en/ajax/AwlHistoricalPrice/getFullDownloadAjax/"
                                + isin
                                + "-XPAR?format=csv&decimal_separator=.&date_form=d/m/Y&adjusted=Y&startdate=2024-01-02&enddate=2024-01-04"))
                    .andRespond(withServerError()));

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 4));

    assertThat(result).isEmpty();
  }

  @Test
  void handlesEmptyDataResponse() {
    FundTicker.getEuronextParisIsins()
        .forEach(
            isin ->
                server
                    .expect(
                        requestTo(
                            "https://live.euronext.com/en/ajax/AwlHistoricalPrice/getFullDownloadAjax/"
                                + isin
                                + "-XPAR?format=csv&decimal_separator=.&date_form=d/m/Y&adjusted=Y&startdate=2024-01-02&enddate=2024-01-02"))
                    .andRespond(withSuccess(emptyCsvResponse(isin), TEXT_PLAIN)));

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 2));

    assertThat(result).isEmpty();
  }

  @Test
  void excludesYesterdaysDataBefore0600CET() {
    // 2024-01-05 04:00 UTC = 05:00 CET (before 06:00 CET cutoff)
    ClockHolder.setClock(Clock.fixed(Instant.parse("2024-01-05T04:00:00Z"), UTC));

    FundTicker.getEuronextParisIsins()
        .forEach(
            isin ->
                server
                    .expect(
                        requestTo(
                            "https://live.euronext.com/en/ajax/AwlHistoricalPrice/getFullDownloadAjax/"
                                + isin
                                + "-XPAR?format=csv&decimal_separator=.&date_form=d/m/Y&adjusted=Y&startdate=2024-01-02&enddate=2024-01-04"))
                    .andRespond(withSuccess(mockCsvResponseForIsin(isin), TEXT_PLAIN)));

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

    FundTicker.getEuronextParisIsins()
        .forEach(
            isin ->
                server
                    .expect(
                        requestTo(
                            "https://live.euronext.com/en/ajax/AwlHistoricalPrice/getFullDownloadAjax/"
                                + isin
                                + "-XPAR?format=csv&decimal_separator=.&date_form=d/m/Y&adjusted=Y&startdate=2024-01-02&enddate=2024-01-04"))
                    .andRespond(withSuccess(mockCsvResponseForIsin(isin), TEXT_PLAIN)));

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

    FundTicker.getEuronextParisIsins()
        .forEach(
            isin ->
                server
                    .expect(
                        requestTo(
                            "https://live.euronext.com/en/ajax/AwlHistoricalPrice/getFullDownloadAjax/"
                                + isin
                                + "-XPAR?format=csv&decimal_separator=.&date_form=d/m/Y&adjusted=Y&startdate=2024-01-02&enddate=2024-01-04"))
                    .andRespond(withSuccess(mockCsvResponseForIsin(isin), TEXT_PLAIN)));

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

  private String mockCsvResponseForIsin(String isin) {
    return """
        "Historical Data"
        "From 2024-01-02 to 2024-01-04"
        %s
        Date;Open;High;Low;Last;Close;Number of Shares;Number of Trades;Turnover
        02/01/2024;4.506;4.506;4.4925;4.497;4.495;3370;6;15147;4.494641
        03/01/2024;4.474;4.511;4.474;4.5065;4.5105;24852;3;112000;4.50669
        04/01/2024;4.4875;4.4875;4.4545;4.4545;4.452;4929;6;22006;4.464614
        """
        .formatted(isin);
  }

  private String mockCsvResponseWithZero(String isin) {
    return """
        "Historical Data"
        "From 2024-01-02 to 2024-01-04"
        %s
        Date;Open;High;Low;Last;Close;Number of Shares;Number of Trades;Turnover
        02/01/2024;4.506;4.506;4.4925;4.497;4.495;3370;6;15147;4.494641
        03/01/2024;0;0;0;0;0;0;0;0;0
        04/01/2024;4.4875;4.4875;4.4545;4.4545;4.452;4929;6;22006;4.464614
        """
        .formatted(isin);
  }

  private String emptyCsvResponse(String isin) {
    return """
        "Historical Data"
        "From 2024-01-02 to 2024-01-02"
        %s
        Date;Open;High;Low;Last;Close;Number of Shares;Number of Trades;Turnover
        """
        .formatted(isin);
  }
}
