package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;

@RestClientTest(MorningstarNavRetriever.class)
class MorningstarNavRetrieverTest {

  @Autowired MorningstarNavRetriever retriever;

  @Autowired MockRestServiceServer server;

  @MockitoBean FundValueRepository fundValueRepository;

  @AfterEach
  void cleanup() {
    server.reset();
  }

  @Test
  void returnsCorrectKey() {
    assertThat(retriever.getKey()).isEqualTo("MORNINGSTAR_NAV");
  }

  @Test
  void parsesNavFromMorningstarApiResponse() {
    mockAllFunds(morningstarResponse("33.99", "2026-02-17"));

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));

    var fund = FundTicker.getMorningstarFunds().getFirst();
    var storageKey = fund.getMorningstarStorageKey().orElseThrow();
    var fundValue =
        result.stream().filter(v -> v.key().equals(storageKey)).findFirst().orElseThrow();
    assertThat(fundValue.date()).isEqualTo(LocalDate.of(2026, 2, 17));
    assertThat(fundValue.value()).isEqualByComparingTo(new BigDecimal("33.99"));
    assertThat(fundValue.provider()).isEqualTo("MORNINGSTAR");
  }

  @Test
  void returnsEmptyListOnApiError() {
    FundTicker.getMorningstarFunds()
        .forEach(
            fund ->
                server
                    .expect(
                        requestTo(
                            "https://lt.morningstar.com/api/rest.svc/klr5zyak8x/security_details/"
                                + fund.getMorningstarId()
                                + "?viewId=MFsnapshot&currencyId=EUR&itype=msid&languageId=en&responseViewFormat=json"))
                    .andRespond(withServerError()));

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));

    assertThat(result).isEmpty();
  }

  @Test
  void filtersOutZeroValues() {
    mockAllFunds(morningstarResponse("0", "2026-02-17"));

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));

    assertThat(result).isEmpty();
  }

  @Test
  void handlesEmptyResponse() {
    mockAllFunds("[]");

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));

    assertThat(result).isEmpty();
  }

  @Test
  void logsWarningWhenPriceDiffersFromExistingValue() {
    var fund = FundTicker.getMorningstarFunds().getFirst();
    var storageKey = fund.getMorningstarStorageKey().orElseThrow();
    var existingValue =
        new FundValue(
            storageKey,
            LocalDate.of(2026, 2, 17),
            new BigDecimal("30.00"),
            "MORNINGSTAR",
            Instant.now());
    when(fundValueRepository.getLatestValue(eq(storageKey), any()))
        .thenReturn(Optional.of(existingValue));

    mockAllFunds(morningstarResponse("33.99", "2026-02-17"));

    var result =
        retriever.retrieveValuesForRange(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));

    assertThat(result).isNotEmpty();
    var fundValue =
        result.stream().filter(v -> v.key().equals(storageKey)).findFirst().orElseThrow();
    assertThat(fundValue.value()).isEqualByComparingTo(new BigDecimal("33.99"));
    verify(fundValueRepository).getLatestValue(storageKey, LocalDate.of(2026, 2, 17));
  }

  private void mockAllFunds(String responseBody) {
    FundTicker.getMorningstarFunds()
        .forEach(
            fund ->
                server
                    .expect(
                        requestTo(
                            "https://lt.morningstar.com/api/rest.svc/klr5zyak8x/security_details/"
                                + fund.getMorningstarId()
                                + "?viewId=MFsnapshot&currencyId=EUR&itype=msid&languageId=en&responseViewFormat=json"))
                    .andRespond(withSuccess(responseBody, APPLICATION_JSON)));
  }

  private String morningstarResponse(String value, String marketDate) {
    return """
        [{"LastPrice":{"Value":%s,"MarketDate":"%s","Currency":{"Id":"EUR"}}}]
        """
        .formatted(value, marketDate);
  }
}
