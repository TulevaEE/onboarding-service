package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.TEXT_PLAIN;
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
import org.springframework.test.web.client.MockRestServiceServer;

@RestClientTest(EuronextValueRetriever.class)
class EuronextValueRetrieverTest {

  @Autowired EuronextValueRetriever retriever;

  @Autowired MockRestServiceServer server;

  @AfterEach
  void cleanup() {
    server.reset();
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
                                + "-XPAR?format=csv&decimal_separator=.&date_form=Y-m-d&adjusted=Y&startdate=2024-01-02&enddate=2024-01-04"))
                    .andRespond(
                        withSuccess(
                            mockCsvResponseForIsin(isin, "2024-01-02", "2024-01-04"), TEXT_PLAIN)));

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
        2024-01-02;4.506;4.506;4.4925;4.497;4.495;3370;6;15147;4.494641
        """
            .formatted(isin);

    server
        .expect(
            requestTo(
                "https://live.euronext.com/en/ajax/AwlHistoricalPrice/getFullDownloadAjax/"
                    + isin
                    + "-XPAR?format=csv&decimal_separator=.&date_form=Y-m-d&adjusted=Y&startdate=2024-01-02&enddate=2024-01-02"))
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
                                + "-XPAR?format=csv&decimal_separator=.&date_form=Y-m-d&adjusted=Y&startdate=2024-01-02&enddate=2024-01-04"))
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
                                + "-XPAR?format=csv&decimal_separator=.&date_form=Y-m-d&adjusted=Y&startdate=2024-01-02&enddate=2024-01-04"))
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
                                + "-XPAR?format=csv&decimal_separator=.&date_form=Y-m-d&adjusted=Y&startdate=2024-01-02&enddate=2024-01-02"))
                    .andRespond(withSuccess(emptyCsvResponse(isin), TEXT_PLAIN)));

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 2));

    assertThat(result).isEmpty();
  }

  private String mockCsvResponseForIsin(String isin, String startDate, String endDate) {
    return """
        "Historical Data"
        "From %s to %s"
        %s
        Date;Open;High;Low;Last;Close;Number of Shares;Number of Trades;Turnover
        %s;4.506;4.506;4.4925;4.497;4.495;3370;6;15147;4.494641
        2024-01-03;4.474;4.511;4.474;4.5065;4.5105;24852;3;112000;4.50669
        %s;4.4875;4.4875;4.4545;4.4545;4.452;4929;6;22006;4.464614
        """
        .formatted(startDate, endDate, isin, startDate, endDate);
  }

  private String mockCsvResponseWithZero(String isin) {
    return """
        "Historical Data"
        "From 2024-01-02 to 2024-01-04"
        %s
        Date;Open;High;Low;Last;Close;Number of Shares;Number of Trades;Turnover
        2024-01-02;4.506;4.506;4.4925;4.497;4.495;3370;6;15147;4.494641
        2024-01-03;0;0;0;0;0;0;0;0;0
        2024-01-04;4.4875;4.4875;4.4545;4.4545;4.452;4929;6;22006;4.464614
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
