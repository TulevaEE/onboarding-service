package ee.tuleva.onboarding.mandate

import com.codeborne.security.mobileid.SignatureFile
import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.epis.contact.UserPreferences
import ee.tuleva.onboarding.fund.Fund
import ee.tuleva.onboarding.fund.FundRepository
import ee.tuleva.onboarding.mandate.content.MandateContentCreator
import ee.tuleva.onboarding.mandate.content.MandateContentFile
import ee.tuleva.onboarding.user.User
import ee.tuleva.onboarding.user.UserService
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.*

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
        UserPreferences sampleUserPreferences = sampleUserPreferences().build()
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

    def "getMandateFiles: on empty user address preferences generates mandate content files with defaults"() {
        given:
        UserPreferences sampleUserPreferences = userPreferencesWithAddressPartiallyEmpty()[0]
        mockMandateFiles(user, mandate.id, sampleUserPreferences)

        1 * mandateContentCreator.
            getContentFiles(_ as User,
                _ as Mandate,
                _ as List,
                { UserPreferences it ->
                    it.addressRow1 == UserPreferences.defaultUserPreferences().addressRow1
                    it.addressRow2 == UserPreferences.defaultUserPreferences().addressRow2
                    it.addressRow3 == UserPreferences.defaultUserPreferences().addressRow3
                    it.getCountry() == UserPreferences.defaultUserPreferences().getCountry()
                    it.getDistrictCode() == UserPreferences.defaultUserPreferences().getDistrictCode()
                    it.getPostalIndex() == UserPreferences.defaultUserPreferences().getPostalIndex()
                }) >> sampleFiles()

        when:
        List<SignatureFile> files = service.getMandateFiles(mandate.id, user.id)

        then:
        files.size() == 1
        files.get(0).mimeType == "html/text"
        files.get(0).content.length == 4
    }

    def "getMandateFiles: on empty user contact preferences generates mandate content files with defaults"() {
        given:
        UserPreferences sampleUserPreferences = userPreferencesWithContactPreferencesPartiallyEmpty()[0]

        mockMandateFiles(user, mandate.id, sampleUserPreferences)

        1 * mandateContentCreator.
            getContentFiles(_ as User,
                _ as Mandate,
                _ as List,
                { UserPreferences it ->
                    it.languagePreference == UserPreferences.defaultUserPreferences().languagePreference
                    it.contactPreference == UserPreferences.defaultUserPreferences().contactPreference
                    it.noticeNeeded == UserPreferences.defaultUserPreferences().noticeNeeded
                }) >> sampleFiles()

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
