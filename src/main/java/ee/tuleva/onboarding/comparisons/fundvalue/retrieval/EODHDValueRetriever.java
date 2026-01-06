package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import static java.math.BigDecimal.ZERO;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.fasterxml.jackson.annotation.JsonProperty;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Slf4j
@ToString(onlyExplicitlyIncluded = true)
public class EODHDValueRetriever implements ComparisonIndexRetriever {

  @ToString.Include public static final String KEY = "EODHD_VALUE";
  public static final String PROVIDER = "EODHD";

  private final RestClient restClient;
  private final String apiToken;

  public EODHDValueRetriever(
      RestClient.Builder restClientBuilder, @Value("${eodhd.api-token:}") String apiToken) {
    this.restClient = restClientBuilder.build();
    this.apiToken = apiToken;
  }

  @Override
  public String getKey() {
    return KEY;
  }

  @Override
  public List<FundValue> retrieveValuesForRange(LocalDate startDate, LocalDate endDate) {
    return FundTicker.getEodhdTickers().stream()
        .flatMap(ticker -> retrieveValuesForTicker(ticker, startDate, endDate).stream())
        .toList();
  }

  private List<FundValue> retrieveValuesForTicker(
      String ticker, LocalDate startDate, LocalDate endDate) {
    var uri = buildUri(ticker, startDate, endDate);

    EODHDResponse[] response =
        restClient.get().uri(uri).accept(APPLICATION_JSON).retrieve().body(EODHDResponse[].class);

    if (response == null) {
      return List.of();
    }

    var now = Instant.now();
    List<FundValue> allValues =
        Arrays.stream(response)
            .map(
                eodhdResponse ->
                    new FundValue(
                        ticker, eodhdResponse.date(), eodhdResponse.adjustedClose(), PROVIDER, now))
            .toList();

    List<FundValue> nonZeroValues =
        allValues.stream().filter(fundValue -> fundValue.value().compareTo(ZERO) != 0).toList();

    int filteredCount = allValues.size() - nonZeroValues.size();
    if (filteredCount > 0) {
      log.warn("Filtered out {} zero-values for ticker {} in date range", filteredCount, ticker);
    }

    return nonZeroValues;
  }

  private String buildUri(String ticker, LocalDate startDate, LocalDate endDate) {
    return UriComponentsBuilder.fromUriString("https://eodhd.com/api/eod/{ticker}")
        .queryParam("api_token", apiToken)
        .queryParam("fmt", "json")
        .queryParam("from", startDate)
        .queryParam("to", endDate)
        .buildAndExpand(ticker)
        .toUriString();
  }

  record EODHDResponse(
      LocalDate date,
      BigDecimal open,
      BigDecimal high,
      BigDecimal low,
      BigDecimal close,
      @JsonProperty("adjusted_close") BigDecimal adjustedClose,
      Long volume) {}
}
