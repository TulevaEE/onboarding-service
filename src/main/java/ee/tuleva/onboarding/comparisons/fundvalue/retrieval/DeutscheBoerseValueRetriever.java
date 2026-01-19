package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import static java.math.BigDecimal.ZERO;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Slf4j
@ToString(onlyExplicitlyIncluded = true)
public class DeutscheBoerseValueRetriever implements ComparisonIndexRetriever {

  @ToString.Include public static final String KEY = "DEUTSCHE_BOERSE_VALUE";
  public static final String PROVIDER = "DEUTSCHE_BOERSE";
  private static final String XETRA_MARKET_IDENTIFIER_CODE = "XETR";

  private final RestClient restClient;

  public DeutscheBoerseValueRetriever(RestClient.Builder restClientBuilder) {
    this.restClient = restClientBuilder.build();
  }

  @Override
  public String getKey() {
    return KEY;
  }

  @Override
  public List<FundValue> retrieveValuesForRange(LocalDate startDate, LocalDate endDate) {
    return FundTicker.getXetraIsins().stream()
        .flatMap(isin -> retrieveValuesForIsin(isin, startDate, endDate).stream())
        .toList();
  }

  private List<FundValue> retrieveValuesForIsin(
      String isin, LocalDate startDate, LocalDate endDate) {
    var uri = buildUri(isin, startDate, endDate);

    DeutscheBoerseResponse response;
    try {
      response =
          restClient
              .get()
              .uri(uri)
              .accept(APPLICATION_JSON)
              .retrieve()
              .body(DeutscheBoerseResponse.class);
    } catch (Exception e) {
      log.error("Failed to retrieve values for ISIN: {}", isin, e);
      return List.of();
    }

    if (response == null || response.data() == null) {
      return List.of();
    }

    var storageKey = isin + "." + XETRA_MARKET_IDENTIFIER_CODE;
    var now = Instant.now();
    List<FundValue> allValues =
        response.data().stream()
            .map(
                priceData ->
                    new FundValue(storageKey, priceData.date(), priceData.close(), PROVIDER, now))
            .toList();

    List<FundValue> nonZeroValues =
        allValues.stream().filter(fundValue -> fundValue.value().compareTo(ZERO) != 0).toList();

    int filteredCount = allValues.size() - nonZeroValues.size();
    if (filteredCount > 0) {
      log.warn("Filtered out {} zero-values for ISIN {} in date range", filteredCount, isin);
    }

    return nonZeroValues;
  }

  private String buildUri(String isin, LocalDate startDate, LocalDate endDate) {
    return UriComponentsBuilder.fromUriString(
            "https://mobile-api.live.deutsche-boerse.com/v1/data/price_history")
        .queryParam("isin", isin)
        .queryParam("mic", XETRA_MARKET_IDENTIFIER_CODE)
        .queryParam("minDate", startDate)
        .queryParam("maxDate", endDate)
        .build()
        .toUriString();
  }

  record DeutscheBoerseResponse(String isin, List<PriceData> data, Integer totalCount) {}

  record PriceData(
      LocalDate date,
      BigDecimal open,
      BigDecimal close,
      BigDecimal high,
      BigDecimal low,
      Long turnoverPieces,
      BigDecimal turnoverEuro) {}
}
