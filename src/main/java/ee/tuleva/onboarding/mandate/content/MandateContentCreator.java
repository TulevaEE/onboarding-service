package ee.tuleva.onboarding.mandate.content;

import ee.tuleva.onboarding.epis.contact.UserPreferences;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.user.User;
import java.util.List;

public interface MandateContentCreator {

  List<MandateContentFile> getContentFiles(
      User user, Mandate mandate, List<Fund> funds, UserPreferences userPreferences);

  MandateContentFile getContentFileForMandateCancellation(
      User user, Mandate mandate, UserPreferences userPreferences, String mandateTypeToCancel);
}
