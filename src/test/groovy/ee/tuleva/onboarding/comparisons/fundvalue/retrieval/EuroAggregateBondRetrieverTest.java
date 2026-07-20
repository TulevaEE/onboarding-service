package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.time.ClockConfig;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.client.MockRestServiceServer;

@RestClientTest(EuroAggregateBondRetriever.class)
@Import(ClockConfig.class)
class EuroAggregateBondRetrieverTest {

  @Autowired EuroAggregateBondRetriever retriever;

  @Autowired MockRestServiceServer server;

  @AfterEach
  void cleanup() {
    server.reset();
  }

  @Test
  void returnsCorrectKey() {
    assertThat(retriever.getKey()).isEqualTo("EURO_AGGREGATE_BOND");
  }

  @Test
  void parsesYahooChartResponseIntoDailyValues() {
    server
        .expect(requestTo(yahooUrl("2016-06-01", "2016-06-04")))
        .andExpect(header("User-Agent", "Mozilla/5.0"))
        .andRespond(
            withSuccess(
                yahooResponse("[1464768000, 1464854400, 1464940800]", "[102.5, null, 103.75]"),
                APPLICATION_JSON));

    var result =
        retriever.retrieveValuesForRange(
            LocalDate.parse("2016-06-01"), LocalDate.parse("2016-06-03"));

    assertThat(result)
        .extracting(FundValue::key, FundValue::date, FundValue::value, FundValue::provider)
        .containsExactly(
            tuple(
                "EURO_AGGREGATE_BOND",
                LocalDate.parse("2016-06-01"),
                new BigDecimal("102.5"),
                "YAHOO"),
            tuple(
                "EURO_AGGREGATE_BOND",
                LocalDate.parse("2016-06-03"),
                new BigDecimal("103.75"),
                "YAHOO"));
  }

  @Test
  void clampsRequestedRangeToCompositeEraBounds() {
    server
        .expect(requestTo(yahooUrl("2016-06-01", "2019-09-10")))
        .andRespond(withSuccess(yahooResponse("[]", "[]"), APPLICATION_JSON));

    retriever.retrieveValuesForRange(LocalDate.parse("2003-01-07"), LocalDate.parse("2026-07-16"));

    server.verify();
  }

  @Test
  void returnsNothingWithoutFetchingWhenRangeIsEntirelyAfterCompositeEra() {
    var result =
        retriever.retrieveValuesForRange(
            LocalDate.parse("2019-09-10"), LocalDate.parse("2026-07-16"));

    assertThat(result).isEmpty();
    server.verify();
  }

  @Test
  void neverReportsStalenessBecauseSeriesIsCompleteHistory() {
    assertThat(retriever.stalenessThreshold()).isGreaterThan(Duration.ofDays(36500));
  }

  private static String yahooUrl(String fromInclusive, String toExclusive) {
    return "https://query1.finance.yahoo.com/v8/finance/chart/IEAG.AS?period1="
        + epochSeconds(fromInclusive)
        + "&period2="
        + epochSeconds(toExclusive)
        + "&interval=1d";
  }

  private static long epochSeconds(String date) {
    return LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toEpochSecond();
  }

  private static String yahooResponse(String timestamps, String adjcloseValues) {
    return """
        {"chart":{"result":[{"timestamp":%s,"indicators":{"adjclose":[{"adjclose":%s}]}}]}}
        """
        .formatted(timestamps, adjcloseValues);
  }
}
