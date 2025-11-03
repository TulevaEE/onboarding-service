package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import static java.util.stream.StreamSupport.stream;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.fasterxml.jackson.databind.JsonNode;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Slf4j
@ToString(onlyExplicitlyIncluded = true)
public class MsciAcwiIndexRetriever implements ComparisonIndexRetriever {
  @ToString.Include public static final String KEY = "MSCI_ACWI";

  private static final String INDEX_CODE = "892400";
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

  private final RestClient restClient;

  public MsciAcwiIndexRetriever(RestClient.Builder restClientBuilder) {
    this.restClient = restClientBuilder.build();
  }

  @Override
  public String getKey() {
    return KEY;
  }

  @Override
  public List<FundValue> retrieveValuesForRange(LocalDate startDate, LocalDate endDate) {
    String fetchUri = buildFetchUri(startDate, endDate);

    JsonNode response =
        restClient.get().uri(fetchUri).accept(APPLICATION_JSON).retrieve().body(JsonNode.class);

    return parseIndexLevels(response, startDate, endDate);
  }

  private List<FundValue> parseIndexLevels(
      JsonNode response, LocalDate startDate, LocalDate endDate) {
    JsonNode indexLevels = response.path("indexes").path("INDEX_LEVELS");

    return stream(indexLevels.spliterator(), false)
        .map(
            node -> {
              String dateString = node.path("calc_date").asText();
              LocalDate date = LocalDate.parse(dateString, DATE_FORMATTER);
              BigDecimal value = BigDecimal.valueOf(node.path("level_eod").asDouble());
              return new FundValue(KEY, date, value);
            })
        .filter(
            fundValue -> {
              LocalDate date = fundValue.date();
              return (startDate.isBefore(date) || startDate.equals(date))
                  && (endDate.isAfter(date) || endDate.equals(date));
            })
        .toList();
  }

  private String buildFetchUri(LocalDate startDate, LocalDate endDate) {

    return UriComponentsBuilder.fromUriString(
            "https://app2.msci.com/products/service/index/indexmaster/getLevelDataForGraph")
        .queryParam("output", "INDEX_LEVELS")
        .queryParam("currency_symbol", "EUR")
        .queryParam("index_variant", "NETR")
        .queryParam("start_date", startDate.format(DATE_FORMATTER))
        .queryParam("end_date", endDate.format(DATE_FORMATTER))
        .queryParam("data_frequency", "DAILY")
        .queryParam("baseValue", "false")
        .queryParam("index_codes", INDEX_CODE)
        .toUriString();
  }
}
