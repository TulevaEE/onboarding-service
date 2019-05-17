package ee.tuleva.onboarding.auth

import ee.tuleva.onboarding.user.MemberFixture
import ee.tuleva.onboarding.user.User
import ee.tuleva.onboarding.epis.contact.UserPreferences

import java.time.Instant

public class UserFixture {

    public static User.UserBuilder sampleUser() {
        return User.builder()
                .id(999)
                .firstName("Jordan")
                .lastName("Valdma")
                .personalCode("38812121215")
                .email("jordan.valdma@gmail.com")
                .phoneNumber("5555555")
                .createdDate(Instant.parse("2017-01-31T10:06:01Z"))
                .updatedDate(Instant.parse("2017-01-31T10:06:01Z"))
                .active(true)
                .member(MemberFixture.sampleMember)
    }

    public static User.UserBuilder sampleUserNonMember() {
        return User.builder()
                .id(999)
                .firstName("Jordan")
                .lastName("Valdma")
                .personalCode("38812121215")
                .email("jordan.valdma@gmail.com")
                .phoneNumber("5555555")
                .createdDate(Instant.parse("2017-01-31T10:06:01Z"))
                .updatedDate(Instant.parse("2017-01-31T10:06:01Z"))
                .active(true)
                .member(null)
    }

    static UserPreferences.UserPreferencesBuilder sampleContactDetails() {
        return UserPreferences.builder()
                .addressRow1("Tatari 19-17")
                .addressRow2("TALLINN")
                .addressRow3("TALLINN")
                .country("EE")
                .postalIndex("12345")
                .districtCode("0784")
                .contactPreference(UserPreferences.ContactPreferenceType.E)
                .languagePreference(UserPreferences.LanguagePreferenceType.EST)
                .noticeNeeded("Y")
                .email("tuleva@tuleva.ee")
                .phoneNumber("+372546545")
                .pensionAccountNumber("993432432")
    }


    public static List<UserPreferences> userPreferencesWithContactPreferencesPartiallyEmpty() {
        return [
                UserPreferences.builder()
                        .addressRow1("Tatari 19-17")
                        .addressRow2("TALLINN")
                        .addressRow3("TALLINN")
                        .country("EE")
                        .postalIndex("12345")
                        .districtCode("0784")
                        .contactPreference(null)
                        .languagePreference(UserPreferences.LanguagePreferenceType.EST)
                        .noticeNeeded("Y")
                        .email("tuleva@tuleva.ee")
                        .build(),
                UserPreferences.builder()
                        .addressRow1("Tatari 19-17")
                        .addressRow2("TALLINN")
                        .addressRow3("TALLINN")
                        .country("EE")
                        .postalIndex("12345")
                        .districtCode("0784")
                        .contactPreference(UserPreferences.ContactPreferenceType.E)
                        .languagePreference(null)
                        .noticeNeeded("Y")
                        .email("tuleva@tuleva.ee")
                        .build(),
                UserPreferences.builder()
                        .addressRow1("Tatari 19-17")
                        .addressRow2("TALLINN")
                        .addressRow3("TALLINN")
                        .country("EE")
                        .postalIndex("12345")
                        .districtCode("0784")
                        .contactPreference(UserPreferences.ContactPreferenceType.E)
                        .languagePreference(UserPreferences.LanguagePreferenceType.EST)
                        .noticeNeeded(null)
                        .email("tuleva@tuleva.ee")
                        .build(),
                UserPreferences.builder()
                        .addressRow1("Tatari 19-17")
                        .addressRow2("TALLINN")
                        .addressRow3("TALLINN")
                        .country("EE")
                        .postalIndex("12345")
                        .districtCode("0784")
                        .contactPreference(UserPreferences.ContactPreferenceType.E)
                        .languagePreference(UserPreferences.LanguagePreferenceType.EST)
                        .noticeNeeded("N")
                        .email(null)
                        .build()

        ]
    }
    public static List<UserPreferences> userPreferencesWithAddressPartiallyEmpty() {
        return [
            UserPreferences.builder()
                .addressRow1("")
                .addressRow2("TALLINN")
                .addressRow3("TALLINN")
                .country("EE")
                .postalIndex("12345")
                .districtCode("0784")
                .contactPreference(UserPreferences.ContactPreferenceType.E)
                .languagePreference(UserPreferences.LanguagePreferenceType.EST)
                .noticeNeeded("Y")
                .email("tuleva@tuleva.ee")
                .build(),
            UserPreferences.builder()
                    .addressRow1("Tatari 19-17")
                    .addressRow2("")
                    .addressRow3("TALLINN")
                    .country("EE")
                    .postalIndex("12345")
                    .districtCode("0784")
                    .contactPreference(UserPreferences.ContactPreferenceType.E)
                    .languagePreference(UserPreferences.LanguagePreferenceType.EST)
                    .noticeNeeded("Y")
                    .email("tuleva@tuleva.ee")
                    .build(),
            UserPreferences.builder()
                    .addressRow1("Tatari 19-17")
                    .addressRow2("TALLINN")
                    .addressRow3("")
                    .country("EE")
                    .postalIndex("12345")
                    .districtCode("0784")
                    .contactPreference(UserPreferences.ContactPreferenceType.E)
                    .languagePreference(UserPreferences.LanguagePreferenceType.EST)
                    .noticeNeeded("Y")
                    .email("tuleva@tuleva.ee")
                    .build(),
            UserPreferences.builder()
                    .addressRow1("Tatari 19-17")
                    .addressRow2("TALLINN")
                    .addressRow3("TALLINN")
                    .country("")
                    .postalIndex("12345")
                    .districtCode("0784")
                    .contactPreference(UserPreferences.ContactPreferenceType.E)
                    .languagePreference(UserPreferences.LanguagePreferenceType.EST)
                    .noticeNeeded("Y")
                    .email("tuleva@tuleva.ee")
                    .build(),
            UserPreferences.builder()
                    .addressRow1("Tatari 19-17")
                    .addressRow2("TALLINN")
                    .addressRow3("TALLINN")
                    .country("EE")
                    .postalIndex("")
                    .districtCode("0784")
                    .contactPreference(UserPreferences.ContactPreferenceType.E)
                    .languagePreference(UserPreferences.LanguagePreferenceType.EST)
                    .noticeNeeded("Y")
                    .email("tuleva@tuleva.ee")
                    .build(),
            UserPreferences.builder()
                    .addressRow1("Tatari 19-17")
                    .addressRow2("TALLINN")
                    .addressRow3("TALLINN")
                    .country("EE")
                    .postalIndex("12345")
                    .districtCode("")
                    .contactPreference(UserPreferences.ContactPreferenceType.E)
                    .languagePreference(UserPreferences.LanguagePreferenceType.EST)
                    .noticeNeeded("Y")
                    .email("tuleva@tuleva.ee")
                    .build(),
                ]
    }

}
