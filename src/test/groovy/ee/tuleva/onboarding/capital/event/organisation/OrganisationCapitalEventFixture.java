package ee.tuleva.onboarding.capital.event.organisation;

import static ee.tuleva.onboarding.capital.event.organisation.OrganisationCapitalEventType.INVESTMENT_RETURN;

import ee.tuleva.onboarding.capital.event.organisation.OrganisationCapitalEvent.OrganisationCapitalEventBuilder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Random;

public class OrganisationCapitalEventFixture {
  static OrganisationCapitalEventBuilder fixture() {
    return OrganisationCapitalEvent.builder()
        .type(INVESTMENT_RETURN)
        .fiatValue(BigDecimal.valueOf((new Random()).nextDouble() * 100000))
        .date(LocalDate.now());
  }
}
