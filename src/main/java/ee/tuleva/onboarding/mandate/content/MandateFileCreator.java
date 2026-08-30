package ee.tuleva.onboarding.mandate.content;

import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.MandateContactDetails;
import ee.tuleva.onboarding.mandate.MandateType;
import ee.tuleva.onboarding.user.User;
import java.util.List;

public interface MandateFileCreator {

  List<MandateContentFile> getContentFiles(
      User user, Mandate mandate, MandateContactDetails contactDetails);

  boolean supports(MandateType mandateType);
}
