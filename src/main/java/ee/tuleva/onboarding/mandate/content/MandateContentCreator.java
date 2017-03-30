package ee.tuleva.onboarding.mandate.content;

import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserPreferences;

import java.util.List;

public interface MandateContentCreator {

    List<MandateContentFile> getContentFiles(User user, Mandate mandate,
                                             List<Fund> funds, UserPreferences userPreferences);

}
