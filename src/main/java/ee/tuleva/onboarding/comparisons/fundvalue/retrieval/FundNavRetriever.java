package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.fund.NavDto;
import ee.tuleva.onboarding.error.exception.ErrorsResponseException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
@ToString(onlyExplicitlyIncluded = true)
public class FundNavRetriever implements ComparisonIndexRetriever {
  public static final String PROVIDER = "PENSIONIKESKUS";

  private final EpisService episService;
  @ToString.Include private final String isin;

  @Override
  public String getKey() {
    return isin;
  }

  @Override
  public List<FundValue> retrieveValuesForRange(LocalDate startDate, LocalDate endDate) {
    List<FundValue> result = new ArrayList<>();
    for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
      try {
        NavDto nav = episService.getNav(isin, date);
        if (nav != null) {
          result.add(toFundValue(nav));
        }
      } catch (ErrorsResponseException e) {
        log.info("No NAV for {} on {}: {}", isin, date, e.getMessage());
      }
    }
    return result;
  }

  private FundValue toFundValue(NavDto nav) {
    return new FundValue(nav.getIsin(), nav.getDate(), nav.getValue(), PROVIDER, Instant.now());
  }
}
