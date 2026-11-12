package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import static org.springframework.http.MediaType.APPLICATION_JSON;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.EODHDValueRetriever.EODHDResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class EuroGovernmentBondRetriever implements ComparisonIndexRetriever {

  public static final String KEY = "EURO_GOVERNMENT_BOND";

  static final LocalDate SERIES_START = LocalDate.of(2003, 1, 7);
  static final LocalDate SERIES_END = EuroAggregateBondRetriever.SERIES_START.minusDays(1);

  private static final String PROVIDER = "EODHD";
  private static final String VANGUARD_EURO_GOVERNMENT_BOND_INDEX_FUND = "IE0007472990.EUFUND";

  private final RestClient restClient;
  private final Clock clock;
  private final String apiToken;

  public EuroGovernmentBondRetriever(
      RestClient.Builder restClientBuilder,
      Clock clock,
      @Value("${eodhd.api-token:}") String apiToken) {
    this.restClient = restClientBuilder.build();
    this.clock = clock;
    this.apiToken = apiToken;
  }

  @Override
  public String getKey() {
    return KEY;
  }

  @Override
  public Duration stalenessThreshold() {
    return Duration.ofDays(365_000);
  }

  @Override
  public List<FundValue> retrieveValuesForRange(LocalDate startDate, LocalDate endDate) {
    var start = startDate.isBefore(SERIES_START) ? SERIES_START : startDate;
    var end = endDate.isAfter(SERIES_END) ? SERIES_END : endDate;
    if (start.isAfter(end)) {
      return List.of();
    }
    var response =
        restClient
            .get()
            .uri(uri(start, end))
            .accept(APPLICATION_JSON)
            .retrieve()
            .body(EODHDResponse[].class);
    var now = clock.instant();
    return Arrays.stream(Objects.requireNonNull(response, "EODHD response is null: key=" + KEY))
        .filter(nav -> nav.adjustedClose().signum() > 0)
        .map(nav -> new FundValue(KEY, nav.date(), nav.adjustedClose(), PROVIDER, now))
        .filter(value -> !value.date().isBefore(start) && !value.date().isAfter(end))
        .toList();
  }

  private String uri(LocalDate start, LocalDate end) {
    return UriComponentsBuilder.fromUriString("https://eodhd.com/api/eod/{ticker}")
        .queryParam("api_token", apiToken)
        .queryParam("fmt", "json")
        .queryParam("from", start)
        .queryParam("to", end)
        .buildAndExpand(VANGUARD_EURO_GOVERNMENT_BOND_INDEX_FUND)
        .toUriString();
  }
}
