package ee.tuleva.onboarding.mandate.content;

import static ee.tuleva.onboarding.fund.Fund.FundStatus.ACTIVE;
import static ee.tuleva.onboarding.mandate.MandateType.PARTIAL_WITHDRAWAL;

import ee.tuleva.onboarding.epis.contact.ContactDetails;
import ee.tuleva.onboarding.epis.mandate.details.PartialWithdrawalMandateDetails;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.MandateType;
import ee.tuleva.onboarding.user.User;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class PartialWithdrawalMandateFileCreator implements MandateFileCreator {

  private final MandateContentService mandateContentService;

  private final FundRepository fundRepository;

  @Override
  public List<MandateContentFile> getContentFiles(
      User user, Mandate mandate, ContactDetails contactDetails) {

    List<Fund> funds = fundRepository.findAllByPillarAndStatus(mandate.getPillar(), ACTIVE);

    String htmlContent =
        mandateContentService.getPartialWithdrawalHtml(user, mandate, contactDetails, funds);

    return List.of(
        MandateContentFile.builder()
            .name(getFileName(mandate))
            .content(htmlContent.getBytes())
            .build());
  }

  public String getFileName(Mandate mandate) {
    var documentNumber = mandate.getId().toString();
    var pillar =
        ((PartialWithdrawalMandateDetails) mandate.getGenericMandateDto().getDetails()).getPillar();

    return switch (pillar) {
      case SECOND -> "yhekordse_valjamakse_avaldus" + documentNumber + ".html";
      case THIRD -> "tagasivotmise_avaldus" + documentNumber + ".html";
    };
  }

  @Override
  public boolean supports(MandateType mandateType) {
    return mandateType == PARTIAL_WITHDRAWAL;
  }
}
