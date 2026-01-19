package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import java.io.BufferedReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Slf4j
@ToString(onlyExplicitlyIncluded = true)
public class EuronextValueRetriever implements ComparisonIndexRetriever {

  @ToString.Include public static final String KEY = "EURONEXT_VALUE";
  public static final String PROVIDER = "EURONEXT";
  private static final String EURONEXT_PARIS_MARKET_IDENTIFIER_CODE = "XPAR";
  private static final int HEADER_LINES_TO_SKIP = 4;

  private final RestClient restClient;

  public EuronextValueRetriever(RestClient.Builder restClientBuilder) {
    this.restClient = restClientBuilder.build();
  }

  @Override
  public String getKey() {
    return KEY;
  }

  @Override
  public List<FundValue> retrieveValuesForRange(LocalDate startDate, LocalDate endDate) {
    return FundTicker.getEuronextParisIsins().stream()
        .flatMap(isin -> retrieveValuesForIsin(isin, startDate, endDate).stream())
        .toList();
  }

  private List<FundValue> retrieveValuesForIsin(
      String isin, LocalDate startDate, LocalDate endDate) {
    var uri = buildUri(isin, startDate, endDate);

    String csvResponse;
    try {
      csvResponse = restClient.get().uri(uri).retrieve().body(String.class);
    } catch (Exception e) {
      log.error("Failed to retrieve values for ISIN: {}", isin, e);
      return List.of();
    }

    if (csvResponse == null || csvResponse.isBlank()) {
      return List.of();
    }

    var storageKey = isin + "." + EURONEXT_PARIS_MARKET_IDENTIFIER_CODE;
    var now = Instant.now();
    List<FundValue> allValues = parseCsvResponse(csvResponse, storageKey, now);

    List<FundValue> nonZeroValues =
        allValues.stream().filter(fundValue -> fundValue.value().compareTo(ZERO) != 0).toList();

    int filteredCount = allValues.size() - nonZeroValues.size();
    if (filteredCount > 0) {
      log.warn("Filtered out {} zero-values for ISIN {} in date range", filteredCount, isin);
    }

    return nonZeroValues;
  }

  private List<FundValue> parseCsvResponse(String csvResponse, String storageKey, Instant now) {
    List<FundValue> values = new ArrayList<>();

    try (var reader = new BufferedReader(new StringReader(csvResponse))) {
      for (int i = 0; i < HEADER_LINES_TO_SKIP; i++) {
        reader.readLine();
      }

      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isBlank()) {
          continue;
        }

        String[] parts = line.split(";");
        if (parts.length < 6) {
          continue;
        }

        var date = LocalDate.parse(parts[0]);
        var closePrice = new BigDecimal(parts[5]);

        values.add(new FundValue(storageKey, date, closePrice, PROVIDER, now));
      }
    } catch (Exception e) {
      log.error("Failed to parse CSV response for key: {}", storageKey, e);
      return List.of();
    }

    return values;
  }

  private String buildUri(String isin, LocalDate startDate, LocalDate endDate) {
    return UriComponentsBuilder.fromUriString(
            "https://live.euronext.com/en/ajax/AwlHistoricalPrice/getFullDownloadAjax/"
                + isin
                + "-"
                + EURONEXT_PARIS_MARKET_IDENTIFIER_CODE)
        .queryParam("format", "csv")
        .queryParam("decimal_separator", ".")
        .queryParam("date_form", "Y-m-d")
        .queryParam("adjusted", "Y")
        .queryParam("startdate", startDate)
        .queryParam("enddate", endDate)
        .build()
        .toUriString();
  }
}
