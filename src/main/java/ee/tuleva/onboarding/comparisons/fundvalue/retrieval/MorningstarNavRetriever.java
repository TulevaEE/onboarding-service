package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import static java.math.BigDecimal.ZERO;

import com.fasterxml.jackson.annotation.JsonProperty;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@Slf4j
public class MorningstarNavRetriever implements ComparisonIndexRetriever {

  public static final String KEY = "MORNINGSTAR_NAV";
  public static final String PROVIDER = "MORNINGSTAR";

  private static final String BASE_URL =
      "https://lt.morningstar.com/api/rest.svc/klr5zyak8x/security_details/";

  private final RestClient restClient;
  private final FundValueRepository fundValueRepository;

  public MorningstarNavRetriever(
      RestClient.Builder restClientBuilder, FundValueRepository fundValueRepository) {
    this.restClient = restClientBuilder.build();
    this.fundValueRepository = fundValueRepository;
  }

  @Override
  public String getKey() {
    return KEY;
  }

  @Override
  public List<FundValue> retrieveValuesForRange(LocalDate startDate, LocalDate endDate) {
    var now = Instant.now();
    List<FundValue> results = new ArrayList<>();
    for (var fund : FundTicker.getMorningstarFunds()) {
      results.addAll(retrieveValueForFund(fund, now));
    }
    return results;
  }

  private List<FundValue> retrieveValueForFund(FundTicker fund, Instant now) {
    var url =
        BASE_URL
            + fund.getMorningstarId()
            + "?viewId=MFsnapshot&currencyId=EUR&itype=msid&languageId=en&responseViewFormat=json";
    var storageKey = fund.getMorningstarStorageKey().orElseThrow();

    List<MorningstarSecurityDetails> response;
    try {
      response = restClient.get().uri(url).retrieve().body(new ParameterizedTypeReference<>() {});
    } catch (Exception e) {
      log.error("Failed to retrieve Morningstar NAV: fund={}", fund.name(), e);
      return List.of();
    }

    if (response == null || response.isEmpty()) {
      return List.of();
    }

    var lastPrice = response.getFirst().lastPrice();
    if (lastPrice == null || lastPrice.value() == null || lastPrice.marketDate() == null) {
      return List.of();
    }

    if (lastPrice.value().compareTo(ZERO) == 0) {
      return List.of();
    }

    fundValueRepository
        .getLatestValue(storageKey, lastPrice.marketDate())
        .ifPresent(
            existing -> {
              if (existing.value().compareTo(lastPrice.value()) != 0) {
                log.warn(
                    "Morningstar NAV price changed: key={}, date={}, existing={}, new={}",
                    storageKey,
                    lastPrice.marketDate(),
                    existing.value(),
                    lastPrice.value());
              }
            });

    return List.of(
        new FundValue(storageKey, lastPrice.marketDate(), lastPrice.value(), PROVIDER, now));
  }

  record MorningstarSecurityDetails(@JsonProperty("LastPrice") MorningstarLastPrice lastPrice) {}

  record MorningstarLastPrice(
      @JsonProperty("Value") BigDecimal value, @JsonProperty("MarketDate") LocalDate marketDate) {}
}
