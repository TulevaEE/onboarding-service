package ee.tuleva.onboarding.mandate

import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.epis.contact.ContactDetails
import ee.tuleva.onboarding.mandate.content.CompositeMandateFileCreator
import ee.tuleva.onboarding.mandate.content.MandateContentFile
import ee.tuleva.onboarding.signature.SignatureFile
import ee.tuleva.onboarding.user.User
import ee.tuleva.onboarding.user.UserService
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture

class MandateFileServiceSpec extends Specification {

  MandateRepository mandateRepository = Mock(MandateRepository)
  EpisService episService = Mock(EpisService)
  CompositeMandateFileCreator compositeMandateFileCreator = Mock(CompositeMandateFileCreator)
  UserService userService = Mock(UserService)

  MandateFileService service = new MandateFileService(mandateRepository,
      episService, compositeMandateFileCreator, userService)

  User user = sampleUser().build()
  Mandate mandate = MandateFixture.sampleMandate()

  def "getMandateFiles: generates mandate content files"() {
    given:
    ContactDetails sampleContactDetails = contactDetailsFixture()

    1 * userService.getById(user.id) >> Optional.of(user)
    1 * mandateRepository.findByIdAndUserId(mandate.id, user.id) >> Mandate.builder().pillar(2).build()
    1 * episService.getContactDetails(user) >> sampleContactDetails

    1 * compositeMandateFileCreator.
        getContentFiles(_ as User,
            _ as Mandate,
            sampleContactDetails) >> sampleFiles()

    when:
    List<SignatureFile> files = service.getMandateFiles(mandate.id, user.id)

    then:
    files.size() == 1
    files.get(0).mimeType == "text/html"
    files.get(0).content.length == 4
  }

  List<MandateContentFile> sampleFiles() {
    return [new MandateContentFile("file", "file".getBytes())]
  }

  def "getMandateFiles based on mandate: generates mandate content files"() {
    given:
    ContactDetails sampleContactDetails = contactDetailsFixture()

    mandate.setUser(user)

    1 * episService.getContactDetails(user) >> sampleContactDetails
    1 * compositeMandateFileCreator.
        getContentFiles(_ as User,
            _ as Mandate,
            _) >> sampleFiles()

    when:
    List<SignatureFile> files = service.getMandateFiles(mandate)

    then:
    files.size() == 1
    files.get(0).mimeType == "text/html"
    files.get(0).content.length == 4
  }

}
