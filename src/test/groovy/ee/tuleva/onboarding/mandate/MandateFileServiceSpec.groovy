package ee.tuleva.onboarding.mandate

import com.codeborne.security.mobileid.SignatureFile
import ee.tuleva.onboarding.auth.UserFixture
import ee.tuleva.onboarding.fund.Fund
import ee.tuleva.onboarding.fund.FundRepository
import ee.tuleva.onboarding.mandate.content.MandateContentCreator
import ee.tuleva.onboarding.mandate.content.MandateContentFile
import ee.tuleva.onboarding.user.CsdUserPreferencesService
import ee.tuleva.onboarding.user.User
import ee.tuleva.onboarding.user.UserPreferences
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUserPreferences
import static ee.tuleva.onboarding.auth.UserFixture.userPreferencesWithAddresPartiallyEmpty
import static ee.tuleva.onboarding.auth.UserFixture.userPreferencesWithContactPreferencesPartiallyEmpty

class MandateFileServiceSpec extends Specification {

    MandateRepository mandateRepository = Mock(MandateRepository);
    FundRepository fundRepository = Mock(FundRepository);
    CsdUserPreferencesService csdUserPreferencesService = Mock(CsdUserPreferencesService);
    MandateContentCreator mandateContentCreator = Mock(MandateContentCreator);

    MandateFileService service = new MandateFileService(mandateRepository, fundRepository,
            csdUserPreferencesService, mandateContentCreator)

    User user = UserFixture.sampleUser()
    Mandate mandate = MandateFixture.sampleMandate()

    def "getMandateFiles: generates mandate content files"() {
        given:
        UserPreferences sampleUserPreferences = sampleUserPreferences()
        mockMandateFiles(user, mandate.id, sampleUserPreferences)

        1 * mandateContentCreator.
                getContentFiles(_ as User,
                        _ as Mandate,
                        _ as List,
                        sampleUserPreferences) >> sampleFiles()

        when:
        List<SignatureFile> files = service.getMandateFiles(mandate.id, user)

        then:
        files.size() == 1
        files.get(0).mimeType == "html/text"
        files.get(0).content.length == 4
    }

    def "getMandateFiles: on empty user address preferences generates mandate content files with defaults"() {
        given:
        UserPreferences sampleUserPreferences = userPreferencesWithAddresPartiallyEmpty()[0]
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
        List<SignatureFile> files = service.getMandateFiles(mandate.id, user)

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
        List<SignatureFile> files = service.getMandateFiles(mandate.id, user)

        then:
        files.size() == 1
        files.get(0).mimeType == "html/text"
        files.get(0).content.length == 4
    }

    List<MandateContentFile> sampleFiles() {
        return [new MandateContentFile("file", "html/text", "file".getBytes())]
    }

    def mockMandateFiles(User user, Long mandateId, UserPreferences userPreferences) {
        1 * mandateRepository.findByIdAndUser(mandateId, user) >> Mandate.builder().build()
        1 * fundRepository.findAll() >> [new Fund(), new Fund()]
        1 * csdUserPreferencesService.getPreferences(user.getPersonalCode()) >> userPreferences
    }

}
