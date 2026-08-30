package ee.tuleva.onboarding.mandate.content;

import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.MandateContactDetails;
import ee.tuleva.onboarding.mandate.MandateType;
import ee.tuleva.onboarding.user.User;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CompositeMandateFileCreator {

  private final List<MandateFileCreator> mandateFileCreators;

  public List<MandateContentFile> getContentFiles(
      User user, Mandate mandate, MandateContactDetails contactDetails) {
    MandateType mandateType = mandate.getMandateType();

    return mandateFileCreators.stream()
        .filter(mandateFileCreator -> mandateFileCreator.supports(mandateType))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Unsupported mandateType: " + mandateType))
        .getContentFiles(user, mandate, contactDetails);
  }
}
