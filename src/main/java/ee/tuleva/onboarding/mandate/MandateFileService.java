package ee.tuleva.onboarding.mandate;

import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.contact.ContactDetails;
import ee.tuleva.onboarding.mandate.content.CompositeMandateFileCreator;
import ee.tuleva.onboarding.signature.SignatureFile;
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
  private final EpisService episService;
  private final CompositeMandateFileCreator compositeMandateFileCreator;
  private final UserService userService;

  public List<SignatureFile> getMandateFiles(Long mandateId, Long userId) {
    User user = userService.getById(userId).orElseThrow();
    Mandate mandate = mandateRepository.findByIdAndUserId(mandateId, userId);

    ContactDetails contactDetails = episService.getContactDetails(user);

    return compositeMandateFileCreator.getContentFiles(user, mandate, contactDetails).stream()
        .map(file -> new SignatureFile(file.getName(), file.getMimeType(), file.getContent()))
        .collect(toList());
  }

  public List<SignatureFile> getMandateFiles(Mandate mandate) {
    var user = mandate.getUser();
    ContactDetails contactDetails = episService.getContactDetails(user);

    return compositeMandateFileCreator.getContentFiles(user, mandate, contactDetails).stream()
        .map(file -> new SignatureFile(file.getName(), file.getMimeType(), file.getContent()))
        .collect(toList());
  }
}
