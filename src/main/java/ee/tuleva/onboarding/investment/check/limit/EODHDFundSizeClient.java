package ee.tuleva.onboarding.investment.check.limit;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

@Slf4j
@Component
class EODHDFundSizeClient {

  private static final String PROVIDER_SUFFIX = ".EODHD";
  private static final String NOT_AVAILABLE = "NA";
  private static final String LISTING_CURRENCY = "General::CurrencyCode";
  private static final String UPDATED_AT = "General::UpdatedAt";
  private static final String ETF_TOTAL_ASSETS = "ETF_Data::TotalAssets";
  private static final String FUND_NET_ASSETS = "MutualFund_Data::Portfolio_Net_Assets";

  private final RestClient restClient;
  private final RetryTemplate retryTemplate;
  private final String apiToken;

  EODHDFundSizeClient(
      RestClient.Builder restClientBuilder, @Value("${eodhd.api-token:}") String apiToken) {
    this.restClient = restClientBuilder.build();
    this.retryTemplate = retryOnTransientFailures();
    this.apiToken = apiToken;
  }

  private static RetryTemplate retryOnTransientFailures() {
    return new RetryTemplate(
        RetryPolicy.builder()
            .includes(HttpServerErrorException.class, ResourceAccessException.class)
            .excludes(HttpClientErrorException.class)
            .maxRetries(2)
            .delay(ofMillis(500))
            .multiplier(2)
            .maxDelay(ofSeconds(2))
            .build());
  }

  FundSize fetch(String eodhdTicker) {
    JsonNode response;
    try {
      response =
          retryTemplate.invoke(
              () ->
                  restClient
                      .get()
                      .uri(buildUri(apiTicker(eodhdTicker)))
                      .accept(APPLICATION_JSON)
                      .retrieve()
                      .body(JsonNode.class));
    } catch (Exception e) {
      var reason = reasonWithoutTheTokenBearingRequestUrl(e);
      log.error("EODHD fundamentals request failed: ticker={}, reason={}", eodhdTicker, reason);
      return new FundSize.Unavailable(reason);
    }
    return fundSize(response);
  }

  private static FundSize fundSize(@Nullable JsonNode response) {
    if (response == null || !response.isObject()) {
      return new FundSize.Unavailable("EODHD returned no fundamentals");
    }
    var amount =
        Stream.of(ETF_TOTAL_ASSETS, FUND_NET_ASSETS)
            .flatMap(field -> positiveAmount(response.path(field)).stream())
            .findFirst();
    if (amount.isEmpty()) {
      return new FundSize.Unavailable("EODHD has no total assets");
    }
    var updatedAt = text(response.path(UPDATED_AT)).flatMap(EODHDFundSizeClient::date).orElse(null);
    return text(response.path(LISTING_CURRENCY))
        .<FundSize>map(currency -> new FundSize.Reported(amount.get(), currency, updatedAt))
        .orElseGet(() -> new FundSize.Unavailable("EODHD has no listing currency"));
  }

  private static String reasonWithoutTheTokenBearingRequestUrl(Exception e) {
    if (e instanceof RestClientResponseException responseException) {
      return "EODHD answered HTTP " + responseException.getStatusCode().value();
    }
    return "EODHD request failed (" + e.getClass().getSimpleName() + ")";
  }

  private static Optional<BigDecimal> positiveAmount(JsonNode node) {
    return text(node).flatMap(EODHDFundSizeClient::parse).filter(value -> value.signum() > 0);
  }

  private static Optional<BigDecimal> parse(String value) {
    try {
      return Optional.of(new BigDecimal(value));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }

  private static Optional<LocalDate> date(String value) {
    try {
      return Optional.of(LocalDate.parse(value));
    } catch (DateTimeParseException e) {
      return Optional.empty();
    }
  }

  private static Optional<String> text(JsonNode node) {
    if (!node.isValueNode() || node.isNull()) {
      return Optional.empty();
    }
    return Optional.of(node.asString().trim())
        .filter(value -> !value.isEmpty() && !value.equals(NOT_AVAILABLE));
  }

  private static String apiTicker(String eodhdTicker) {
    return eodhdTicker.endsWith(PROVIDER_SUFFIX)
        ? eodhdTicker.substring(0, eodhdTicker.length() - PROVIDER_SUFFIX.length())
        : eodhdTicker;
  }

  private String buildUri(String ticker) {
    return UriComponentsBuilder.fromUriString("https://eodhd.com/api/fundamentals/{ticker}")
        .queryParam("api_token", apiToken)
        .queryParam("fmt", "json")
        .queryParam(
            "filter",
            String.join(",", LISTING_CURRENCY, UPDATED_AT, ETF_TOTAL_ASSETS, FUND_NET_ASSETS))
        .buildAndExpand(ticker)
        .toUriString();
  }

  sealed interface FundSize {

    record Reported(BigDecimal amount, String listingCurrency, @Nullable LocalDate updatedAt)
        implements FundSize {}

    record Unavailable(String reason) implements FundSize {}
  }
}
