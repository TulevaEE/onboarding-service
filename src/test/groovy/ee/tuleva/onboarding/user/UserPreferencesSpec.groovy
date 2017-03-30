package ee.tuleva.onboarding.user

import spock.lang.Specification

class UserPreferencesSpec extends Specification {
    def "DefaultUserPreferences: gets correct user preferences"() {
        when:
        UserPreferences userPreferences = UserPreferences.defaultUserPreferences()
        then:
        userPreferences.addressRow1.equals("Tuleva, Telliskivi 60")
        userPreferences.addressRow2.equals("TALLINN")
        userPreferences.addressRow3.equals("TALLINN")
        userPreferences.country.equals("EE")
        userPreferences.postalIndex.equals("10412")
        userPreferences.districtCode.equals("0784")

        userPreferences.contactPreference.equals(UserPreferences.ContactPreferenceType.valueOf("E"))
        userPreferences.languagePreference.equals(
                (UserPreferences.LanguagePreferenceType.valueOf(
                        "EST")
                ))
        userPreferences.noticeNeeded.equals(1)
    }
}
