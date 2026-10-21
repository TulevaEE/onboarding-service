package ee.tuleva.onboarding.mandate.content;

import static ee.tuleva.onboarding.mandate.MandateType.REJOIN;

import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.MandateContactDetails;
import ee.tuleva.onboarding.mandate.MandateType;
import ee.tuleva.onboarding.user.User;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class RejoinMandateFileCreator implements MandateFileCreator {

  private final MandateContentService mandateContentService;

  @Override
  public List<MandateContentFile> getContentFiles(
      User user, Mandate mandate, MandateContactDetails contactDetails) {
    return List.of(
        MandateContentFile.builder()
            .name("maksete_tegemise_avaldus_" + mandate.getIdOrThrow() + ".html")
            .content(mandateContentService.getRejoinHtml(user, mandate, contactDetails).getBytes())
            .build());
  }

  @Override
  public boolean supports(MandateType mandateType) {
    return mandateType == REJOIN;
  }
}
