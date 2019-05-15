package ee.tuleva.onboarding.capital.event.organisation;

import ee.tuleva.onboarding.capital.event.organisation.OrganisationCapitalEvent.OrganisationCapitalEventBuilder;

import java.math.BigDecimal;
import java.util.Random;

public class OrganisationCapitalEventFixture {
    static OrganisationCapitalEventBuilder fixture() {
        return OrganisationCapitalEvent.builder()
            .type(OrganisationCapitalEventType.FIAT_RETURN)
            .fiatValue(new BigDecimal((new Random()).nextDouble() * 100000));
    }
}
