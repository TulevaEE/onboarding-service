package ee.tuleva.onboarding.mandate.content;

import static ee.tuleva.onboarding.fund.Fund.FundStatus.ACTIVE;
import static ee.tuleva.onboarding.mandate.MandateType.UNKNOWN;

import ee.tuleva.onboarding.epis.contact.ContactDetails;
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
public class NonGenericMandateFileCreator implements MandateFileCreator {

  private final FundRepository fundRepository;

  private final MandateContentCreator mandateContentCreator;

  @Override
  public List<MandateContentFile> getContentFiles(
      User user, Mandate mandate, ContactDetails contactDetails) {
    List<Fund> funds = fundRepository.findAllByPillarAndStatus(mandate.getPillar(), ACTIVE);

    return mandateContentCreator.getContentFiles(user, mandate, funds, contactDetails);
  }

  @Override
  public boolean supports(MandateType mandateType) {
    return mandateType == UNKNOWN;
  }
}
