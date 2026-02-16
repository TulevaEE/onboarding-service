package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import static java.math.BigDecimal.ZERO;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.TEXT_HTML;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ee.tuleva.onboarding.time.ClockConfig;
import ee.tuleva.onboarding.time.ClockHolder;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.client.MockRestServiceServer;

@RestClientTest(BlackRockFundValueRetriever.class)
@Import(ClockConfig.class)
class BlackRockFundValueRetrieverTest {

  @Autowired BlackRockFundValueRetriever retriever;

  @Autowired MockRestServiceServer server;

  @AfterEach
  void cleanup() {
    server.reset();
    ClockHolder.setDefaultClock();
  }

  @Test
  void returnsCorrectKey() {
    assertThat(retriever.getKey()).isEqualTo("BLACKROCK_VALUE");
  }

  @Test
  void parsesNavDataWithCorrectMonthOffset() {
    var fund = FundTicker.getBlackrockFunds().getFirst();
    var storageKey = fund.getBlackrockStorageKey().orElseThrow();

    mockAllFunds(mockNavDataResponse());

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 4));

    var fundValue =
        result.stream().filter(value -> value.key().equals(storageKey)).findFirst().orElseThrow();
    assertThat(fundValue.key()).isEqualTo(storageKey);
    // Date.UTC(2024,0,2) means January 2 (month is 0-indexed)
    assertThat(fundValue.date()).isEqualTo(LocalDate.of(2024, 1, 2));
    assertThat(fundValue.value()).isEqualByComparingTo(new BigDecimal("30.50"));
  }

  @Test
  void retrievesFundValuesForAllBlackrockFunds() {
    mockAllFunds(mockNavDataResponse());

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 4));

    assertThat(result)
        .isNotEmpty()
        .allSatisfy(
            fundValue -> {
              assertThat(fundValue.provider()).isEqualTo("BLACKROCK");
              assertThat(fundValue.updatedAt()).isNotNull();
              assertThat(fundValue.key()).endsWith(".BLACKROCK");
            });
  }

  @Test
  void filtersOutZeroValues() {
    mockAllFunds(mockNavDataResponseWithZero());

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 4));

    assertThat(result)
        .isNotEmpty()
        .allSatisfy(fundValue -> assertThat(fundValue.value()).isNotEqualTo(ZERO));
  }

  @Test
  void returnsEmptyListOnApiError() {
    FundTicker.getBlackrockFunds()
        .forEach(
            fund ->
                server
                    .expect(
                        requestTo(
                            "https://www.blackrock.com/se/individual/products/"
                                + fund.getBlackrockProductId()
                                + "/"))
                    .andRespond(withServerError()));

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 4));

    assertThat(result).isEmpty();
  }

  @Test
  void handlesEmptyResponse() {
    mockAllFunds("<html><body>No data</body></html>");

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 4));

    assertThat(result).isEmpty();
  }

  @Test
  void excludesYesterdaysDataBefore0600CET() {
    // 2024-01-05 05:00 UTC = 05:00 Dublin time (before 06:00 cutoff)
    ClockHolder.setClock(Clock.fixed(Instant.parse("2024-01-05T05:00:00Z"), UTC));

    mockAllFunds(mockNavDataResponse());

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
    // 2024-01-05 06:00 UTC = 06:00 Dublin time (at/after 06:00 cutoff)
    ClockHolder.setClock(Clock.fixed(Instant.parse("2024-01-05T06:00:00Z"), UTC));

    mockAllFunds(mockNavDataResponse());

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
    // 2024-01-04 20:00 UTC = 20:00 Dublin time (well after any close)
    ClockHolder.setClock(Clock.fixed(Instant.parse("2024-01-04T20:00:00Z"), UTC));

    mockAllFunds(mockNavDataResponse());

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 4));

    // At 21:00 CET on Jan 4: latestFinalizedDate = Jan 3 (yesterday)
    assertThat(result)
        .isNotEmpty()
        .anyMatch(fundValue -> fundValue.date().equals(LocalDate.of(2024, 1, 2)))
        .anyMatch(fundValue -> fundValue.date().equals(LocalDate.of(2024, 1, 3)))
        .noneMatch(fundValue -> fundValue.date().equals(LocalDate.of(2024, 1, 4)));
  }

  private void mockAllFunds(String responseBody) {
    FundTicker.getBlackrockFunds()
        .forEach(
            fund ->
                server
                    .expect(
                        requestTo(
                            "https://www.blackrock.com/se/individual/products/"
                                + fund.getBlackrockProductId()
                                + "/"))
                    .andRespond(withSuccess(responseBody, TEXT_HTML)));
  }

  private String mockNavDataResponse() {
    // Date.UTC months are 0-indexed: 0=Jan, 1=Feb, etc.
    return """
        <html><script>
        navData = [{x:Date.UTC(2024,0,2),y:Number((30.50).toFixed(2)),formattedX: "02.Jan.2024"},\
        {x:Date.UTC(2024,0,3),y:Number((31.25).toFixed(2)),formattedX: "03.Jan.2024"},\
        {x:Date.UTC(2024,0,4),y:Number((32.00).toFixed(2)),formattedX: "04.Jan.2024"}];
        </script></html>
        """;
  }

  private String mockNavDataResponseWithZero() {
    return """
        <html><script>
        navData = [{x:Date.UTC(2024,0,2),y:Number((30.50).toFixed(2)),formattedX: "02.Jan.2024"},\
        {x:Date.UTC(2024,0,3),y:Number((0).toFixed(2)),formattedX: "03.Jan.2024"},\
        {x:Date.UTC(2024,0,4),y:Number((32.00).toFixed(2)),formattedX: "04.Jan.2024"}];
        </script></html>
        """;
  }
}
