package ee.tuleva.onboarding.mandate.content;

import static ee.tuleva.onboarding.mandate.MandateType.FUND_PENSION_OPENING;

import ee.tuleva.onboarding.epis.contact.ContactDetails;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.MandateType;
import ee.tuleva.onboarding.user.User;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class FundPensionOpeningMandateFileCreator implements MandateFileCreator {

  private final MandateContentService mandateContentService;

  @Override
  public List<MandateContentFile> getContentFiles(
      User user, Mandate mandate, ContactDetails contactDetails) {

    String htmlContent =
        mandateContentService.getFundPensionOpeningHtml(user, mandate, contactDetails);
    String documentNumber = mandate.getId().toString();

    return List.of(
        MandateContentFile.builder()
            .name("fondipensioni_avamise_avaldus_" + documentNumber + ".html")
            .content(htmlContent.getBytes())
            .build());
  }

  @Override
  public boolean supports(MandateType mandateType) {
    return mandateType == FUND_PENSION_OPENING;
  }
}
