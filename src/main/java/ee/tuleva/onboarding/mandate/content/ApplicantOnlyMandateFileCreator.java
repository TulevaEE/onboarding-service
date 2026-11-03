package ee.tuleva.onboarding.mandate.content;

import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.MandateContactDetails;
import ee.tuleva.onboarding.mandate.MandateType;
import ee.tuleva.onboarding.user.User;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
abstract class ApplicantOnlyMandateFileCreator implements MandateFileCreator {

  private final MandateContentService mandateContentService;
  private final MandateType mandateType;
  private final String applicationName;
  private final String template;

  @Override
  public List<MandateContentFile> getContentFiles(
      User user, Mandate mandate, MandateContactDetails contactDetails) {
    return List.of(
        MandateContentFile.builder()
            .name(applicationName + "_" + mandate.getIdOrThrow() + ".html")
            .content(
                mandateContentService
                    .getApplicantOnlyHtml(template, user, mandate, contactDetails)
                    .getBytes())
            .build());
  }

  @Override
  public boolean supports(MandateType mandateType) {
    return this.mandateType == mandateType;
  }
}
