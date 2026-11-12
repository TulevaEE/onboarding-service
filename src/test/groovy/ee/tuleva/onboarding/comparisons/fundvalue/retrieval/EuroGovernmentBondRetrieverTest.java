package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.time.ClockConfig;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;

@RestClientTest(EuroGovernmentBondRetriever.class)
@Import(ClockConfig.class)
@TestPropertySource(properties = "eodhd.api-token=test-token")
class EuroGovernmentBondRetrieverTest {

  @Autowired EuroGovernmentBondRetriever retriever;

  @Autowired MockRestServiceServer server;

  @AfterEach
  void cleanup() {
    server.reset();
  }

  @Test
  void returnsItsKey() {
    assertThat(retriever.getKey()).isEqualTo("EURO_GOVERNMENT_BOND");
  }

  @Test
  void readsTheBondFundsDailyNavsFromEodhd() {
    server
        .expect(requestTo(eodhdUrl("2003-01-07", "2003-01-08")))
        .andRespond(
            withSuccess(
                """
                [
                  {"date":"2003-01-07","open":119.0873,"high":119.0873,"low":119.0873,"close":119.0873,"adjusted_close":119.0873,"volume":0},
                  {"date":"2003-01-08","open":119.2012,"high":119.2012,"low":119.2012,"close":119.2012,"adjusted_close":119.2012,"volume":0}
                ]
                """,
                APPLICATION_JSON));

    var result =
        retriever.retrieveValuesForRange(
            LocalDate.parse("2003-01-07"), LocalDate.parse("2003-01-08"));

    assertThat(result)
        .extracting(FundValue::key, FundValue::date, FundValue::value, FundValue::provider)
        .containsExactly(
            tuple(
                "EURO_GOVERNMENT_BOND",
                LocalDate.parse("2003-01-07"),
                new BigDecimal("119.0873"),
                "EODHD"),
            tuple(
                "EURO_GOVERNMENT_BOND",
                LocalDate.parse("2003-01-08"),
                new BigDecimal("119.2012"),
                "EODHD"));
  }

  @Test
  void leavesOutANavThatIsNotPositiveRatherThanStoringItForGood() {
    server
        .expect(requestTo(eodhdUrl("2003-01-07", "2003-01-09")))
        .andRespond(
            withSuccess(
                """
                [
                  {"date":"2003-01-07","open":119.0873,"high":119.0873,"low":119.0873,"close":119.0873,"adjusted_close":119.0873,"volume":0},
                  {"date":"2003-01-08","open":0,"high":0,"low":0,"close":0,"adjusted_close":0,"volume":0},
                  {"date":"2003-01-09","open":119.3,"high":119.3,"low":119.3,"close":119.3,"adjusted_close":119.3,"volume":0}
                ]
                """,
                APPLICATION_JSON));

    var result =
        retriever.retrieveValuesForRange(
            LocalDate.parse("2003-01-07"), LocalDate.parse("2003-01-09"));

    assertThat(result)
        .extracting(FundValue::date)
        .containsExactly(LocalDate.parse("2003-01-07"), LocalDate.parse("2003-01-09"));
  }

  @Test
  void asksOnlyForTheYearsBeforeTheAggregateBondSeriesStarts() {
    server
        .expect(requestTo(eodhdUrl("2003-01-07", "2016-05-31")))
        .andRespond(withSuccess("[]", APPLICATION_JSON));

    retriever.retrieveValuesForRange(LocalDate.parse("2003-01-07"), LocalDate.parse("2026-11-12"));

    server.verify();
  }

  @Test
  void returnsNothingWithoutAskingOnceTheRangeStartsAfterTheSeries() {
    var result =
        retriever.retrieveValuesForRange(
            LocalDate.parse("2016-06-01"), LocalDate.parse("2026-11-12"));

    assertThat(result).isEmpty();
    server.verify();
  }

  @Test
  void neverReportsStalenessBecauseTheSeriesIsClosedHistory() {
    assertThat(retriever.stalenessThreshold()).isGreaterThan(Duration.ofDays(36500));
  }

  private static String eodhdUrl(String from, String to) {
    return "https://eodhd.com/api/eod/IE0007472990.EUFUND?api_token=test-token&fmt=json&from="
        + from
        + "&to="
        + to;
  }
}
