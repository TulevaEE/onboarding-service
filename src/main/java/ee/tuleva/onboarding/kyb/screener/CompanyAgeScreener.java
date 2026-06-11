package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.KybCheckType.COMPANY_AGE;

import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.KybCompanyData;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CompanyAgeScreener implements KybScreener {

  private static final long MINIMUM_COMPANY_AGE_YEARS = 1;

  private final Clock clock;

  @Override
  public List<KybCheck> screen(KybCompanyData companyData) {
    var foundingDate = companyData.foundingDate();
    if (foundingDate == null) {
      return List.of();
    }
    var threshold = LocalDate.now(clock).minusYears(MINIMUM_COMPANY_AGE_YEARS);
    var establishedLongEnough = !foundingDate.isAfter(threshold);
    return List.of(
        new KybCheck(
            COMPANY_AGE, establishedLongEnough, Map.of("foundingDate", foundingDate.toString())));
  }
}
