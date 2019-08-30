package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.fund.NavDto;
import ee.tuleva.onboarding.error.exception.ErrorsResponseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Slf4j
public class FundNavRetriever implements ComparisonIndexRetriever {
    private final EpisService episService;
    private final String isin;

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
                if (nav != null) result.add(toFundValue(nav));
            }
            catch (ErrorsResponseException e) {
                log.info("No NAV for {} on {}: {}", isin, date, e.getMessage());
            }
        }
        return result;
    }

    private FundValue toFundValue(NavDto nav) {
        return new FundValue(nav.getIsin(), nav.getDate(), nav.getValue());
    }
}
