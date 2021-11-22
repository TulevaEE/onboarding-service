package ee.tuleva.onboarding.mandate;

import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.contact.ContactDetails;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.mandate.content.MandateContentCreator;
import ee.tuleva.onboarding.mandate.signature.SignatureFile;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MandateFileService {

  private final MandateRepository mandateRepository;
  private final FundRepository fundRepository;
  private final EpisService episService;
  private final MandateContentCreator mandateContentCreator;
  private final UserService userService;

  public List<SignatureFile> getMandateFiles(Long mandateId, Long userId) {
    User user = userService.getById(userId);
    Mandate mandate = mandateRepository.findByIdAndUserId(mandateId, userId);

    List<Fund> funds = fundRepository.findAllByPillar(mandate.getPillar());

    ContactDetails contactDetails = episService.getContactDetails(user);

    return mandateContentCreator.getContentFiles(user, mandate, funds, contactDetails).stream()
        .map(file -> new SignatureFile(file.getName(), file.getMimeType(), file.getContent()))
        .collect(toList());
  }
}
