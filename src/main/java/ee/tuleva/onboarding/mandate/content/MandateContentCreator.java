package ee.tuleva.onboarding.mandate.content;

import ee.tuleva.domain.fund.Fund;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.user.User;

import java.util.List;

public abstract class MandateContentCreator {

    User user;
    Mandate mandate;
    List<Fund> funds;

    MandateContentCreator(User user, Mandate mandate, List<Fund> funds) {
        this.user = user;
        this.mandate = mandate;
        this.funds = funds;
    }

    public abstract List<MandateContentFile> getContentFiles();

}
