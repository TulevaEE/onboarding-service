package ee.tuleva.onboarding.mandate.content;

import static ee.tuleva.onboarding.mandate.MandateType.WITHDRAWAL_CANCELLATION;

import ee.tuleva.onboarding.applicationtype.ApplicationType;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.MandateContactDetails;
import ee.tuleva.onboarding.mandate.MandateType;
import ee.tuleva.onboarding.user.User;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class WithdrawalCancellationMandateFileCreator implements MandateFileCreator {

  private final MandateContentService mandateContentService;

  @Override
  public List<MandateContentFile> getContentFiles(
      User user, Mandate mandate, MandateContactDetails contactDetails) {

    String htmlContent =
        mandateContentService.getMandateCancellationHtml(
            user, mandate, contactDetails, ApplicationType.WITHDRAWAL);
    String documentNumber = mandate.getIdOrThrow().toString();

    return List.of(
        MandateContentFile.builder()
            .name("avalduse_tyhistamise_avaldus_" + documentNumber + ".html")
            .content(htmlContent.getBytes())
            .build());
  }

  @Override
  public boolean supports(MandateType mandateType) {
    return mandateType == WITHDRAWAL_CANCELLATION;
  }
}
