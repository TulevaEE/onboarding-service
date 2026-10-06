package ee.tuleva.onboarding.investment.check.limit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ee.tuleva.onboarding.investment.check.limit.EODHDFundSizeClient.FundSize;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;

@RestClientTest(EODHDFundSizeClient.class)
@TestPropertySource(properties = "eodhd.api-token=test-token")
class EODHDFundSizeClientTest {

  private static final LocalDate UPDATED = LocalDate.of(2026, 10, 3);

  @Autowired EODHDFundSizeClient client;

  @Autowired MockRestServiceServer server;

  @AfterEach
  void verifyAllRequestsWereMade() {
    server.verify();
  }

  @Test
  void anEtfsTotalAssets_isReportedInItsListingCurrency() {
    respondFor(
        "ESGM.XETRA",
        """
        {"General::CurrencyCode":"EUR","General::UpdatedAt":"2026-10-03","ETF_Data::TotalAssets":"150000000.00","MutualFund_Data::Portfolio_Net_Assets":"NA"}
        """);

    assertThat(client.fetch("ESGM.XETRA"))
        .isEqualTo(new FundSize.Reported(new BigDecimal("150000000.00"), "EUR", UPDATED));
  }

  @Test
  void aTickerCarryingTheEodhdStorageSuffix_isAskedForWithoutIt() {
    respondFor(
        "USAS.PA",
        """
        {"General::CurrencyCode":"EUR","General::UpdatedAt":"2026-10-03","ETF_Data::TotalAssets":"1200000000","MutualFund_Data::Portfolio_Net_Assets":"NA"}
        """);

    assertThat(client.fetch("USAS.PA.EODHD"))
        .isEqualTo(new FundSize.Reported(new BigDecimal("1200000000"), "EUR", UPDATED));
  }

  @Test
  void aNonListedFundsNetAssets_areReportedWhenThereIsNoEtfFigure() {
    respondFor(
        "IE00BFG1TM61.EUFUND",
        """
        {"General::CurrencyCode":"EUR","General::UpdatedAt":"2026-10-03","ETF_Data::TotalAssets":"NA","MutualFund_Data::Portfolio_Net_Assets":"4500000000"}
        """);

    assertThat(client.fetch("IE00BFG1TM61.EUFUND"))
        .isEqualTo(new FundSize.Reported(new BigDecimal("4500000000"), "EUR", UPDATED));
  }

  @Test
  void theNonListedFundEodhdHoldsNoFundamentalsFor_isUnavailable() {
    respondFor(
        "IE00BFG1TM61.EUFUND",
        """
        {"General::CurrencyCode":"NA","General::UpdatedAt":"NA","ETF_Data::TotalAssets":"NA","MutualFund_Data::Portfolio_Net_Assets":"NA"}
        """);

    assertThat(client.fetch("IE00BFG1TM61.EUFUND"))
        .isEqualTo(new FundSize.Unavailable("EODHD has no total assets"));
  }

  @Test
  void aResponseThatIsNotAnObject_isUnavailable() {
    respondFor("ESGM.XETRA", "[]");

    assertThat(client.fetch("ESGM.XETRA"))
        .isEqualTo(new FundSize.Unavailable("EODHD returned no fundamentals"));
  }

  @Test
  void aFundSizeWithoutAnUpdateDate_isReportedWithTheDateUnknown() {
    respondFor(
        "ESGM.XETRA",
        """
        {"General::CurrencyCode":"EUR","General::UpdatedAt":"NA","ETF_Data::TotalAssets":"150000000","MutualFund_Data::Portfolio_Net_Assets":"NA"}
        """);

    assertThat(client.fetch("ESGM.XETRA"))
        .isEqualTo(new FundSize.Reported(new BigDecimal("150000000"), "EUR", null));
  }

  @Test
  void totalAssetsThatAreNotAScalar_areUnavailableRatherThanThrowing() {
    respondFor(
        "ESGM.XETRA",
        """
        {"General::CurrencyCode":"EUR","General::UpdatedAt":"2026-10-03","ETF_Data::TotalAssets":{"value":"1"},"MutualFund_Data::Portfolio_Net_Assets":"NA"}
        """);

    assertThat(client.fetch("ESGM.XETRA"))
        .isEqualTo(new FundSize.Unavailable("EODHD has no total assets"));
  }

  @Test
  void noTotalAssetsInEitherField_isUnavailable() {
    respondFor(
        "ESGM.XETRA",
        """
        {"General::CurrencyCode":"EUR","General::UpdatedAt":"2026-10-03","ETF_Data::TotalAssets":"0.00","MutualFund_Data::Portfolio_Net_Assets":"NA"}
        """);

    assertThat(client.fetch("ESGM.XETRA"))
        .isEqualTo(new FundSize.Unavailable("EODHD has no total assets"));
  }

  @Test
  void totalAssetsWithoutAListingCurrency_isUnavailable() {
    respondFor(
        "ESGM.XETRA",
        """
        {"General::CurrencyCode":"NA","ETF_Data::TotalAssets":"150000000","MutualFund_Data::Portfolio_Net_Assets":"NA"}
        """);

    assertThat(client.fetch("ESGM.XETRA"))
        .isEqualTo(new FundSize.Unavailable("EODHD has no listing currency"));
  }

  @Test
  void anUnknownTicker_isUnavailableWithTheHttpStatus_withoutRetrying() {
    server
        .expect(requestTo(uri("ESGM.XETRA")))
        .andRespond(withStatus(NOT_FOUND).body("Ticker Not Found."));

    assertThat(client.fetch("ESGM.XETRA"))
        .isEqualTo(new FundSize.Unavailable("EODHD answered HTTP 404"));
  }

  @Test
  void aServerErrorThatClearsOnRetry_isReported() {
    server.expect(once(), requestTo(uri("ESGM.XETRA"))).andRespond(withStatus(SERVICE_UNAVAILABLE));
    respondFor(
        "ESGM.XETRA",
        """
        {"General::CurrencyCode":"EUR","General::UpdatedAt":"2026-10-03","ETF_Data::TotalAssets":"150000000","MutualFund_Data::Portfolio_Net_Assets":"NA"}
        """);

    assertThat(client.fetch("ESGM.XETRA"))
        .isEqualTo(new FundSize.Reported(new BigDecimal("150000000"), "EUR", UPDATED));
  }

  @Test
  void anIoFailureThatOutlastsTheRetries_isUnavailableWithoutTheTokenBearingRequestUrl() {
    server
        .expect(times(3), requestTo(uri("ESGM.XETRA")))
        .andRespond(withException(new IOException("connect timed out")));

    assertThat(client.fetch("ESGM.XETRA"))
        .isEqualTo(new FundSize.Unavailable("EODHD request failed (ResourceAccessException)"));
  }

  private void respondFor(String apiTicker, String body) {
    server
        .expect(requestTo(uri(apiTicker)))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
  }

  private static String uri(String apiTicker) {
    return "https://eodhd.com/api/fundamentals/"
        + apiTicker
        + "?api_token=test-token&fmt=json"
        + "&filter=General::CurrencyCode,General::UpdatedAt,ETF_Data::TotalAssets,"
        + "MutualFund_Data::Portfolio_Net_Assets";
  }
}
