package ee.tuleva.onboarding.mandate

import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.epis.contact.UserPreferences
import ee.tuleva.onboarding.fund.Fund
import ee.tuleva.onboarding.fund.FundRepository
import ee.tuleva.onboarding.mandate.content.MandateContentCreator
import ee.tuleva.onboarding.mandate.content.MandateContentFile
import ee.tuleva.onboarding.mandate.signature.SignatureFile
import ee.tuleva.onboarding.user.User
import ee.tuleva.onboarding.user.UserService
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture

class MandateFileServiceSpec extends Specification {

    MandateRepository mandateRepository = Mock(MandateRepository)
    FundRepository fundRepository = Mock(FundRepository)
    EpisService episService = Mock(EpisService)
    MandateContentCreator mandateContentCreator = Mock(MandateContentCreator)
    UserService userService = Mock(UserService)

    MandateFileService service = new MandateFileService(mandateRepository, fundRepository,
        episService, mandateContentCreator, userService)

    User user = sampleUser().build()
    Mandate mandate = MandateFixture.sampleMandate()

    def "getMandateFiles: generates mandate content files"() {
        given:
        UserPreferences sampleUserPreferences = contactDetailsFixture()
        mockMandateFiles(user, mandate.id, sampleUserPreferences)

        1 * mandateContentCreator.
            getContentFiles(_ as User,
                _ as Mandate,
                _ as List,
                sampleUserPreferences) >> sampleFiles()

        when:
        List<SignatureFile> files = service.getMandateFiles(mandate.id, user.id)

        then:
        files.size() == 1
        files.get(0).mimeType == "html/text"
        files.get(0).content.length == 4
    }

    List<MandateContentFile> sampleFiles() {
        return [new MandateContentFile("file", "html/text", "file".getBytes())]
    }

    def mockMandateFiles(User user, Long mandateId, UserPreferences userPreferences) {
        1 * userService.getById(user.id) >> user
        1 * mandateRepository.findByIdAndUserId(mandateId, user.id) >> Mandate.builder().pillar(2).build()
        1 * fundRepository.findAllByPillar(mandate.pillar) >> [new Fund(), new Fund()]
        1 * episService.getContactDetails(user) >> userPreferences
    }

}
