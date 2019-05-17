package ee.tuleva.onboarding.epis.contact;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
// TODO: rename to ContactDetails
public class UserPreferences {

    public enum ContactPreferenceType {E, P} // E - email, P - postal

    private String firstName;

    private String lastName;

    private String personalCode;

    private ContactPreferenceType contactPreference;

    private String districtCode;

    private String addressRow1;

    private String addressRow2;

    private String addressRow3;

    private String postalIndex;

    private String country;

    public enum LanguagePreferenceType {EST, RUS, ENG}

    private LanguagePreferenceType languagePreference;

    private String noticeNeeded; // boolean { 'Y', 'N' }

    private String email;

    private String phoneNumber;

    private String pensionAccountNumber;

    public static UserPreferences defaultUserPreferences() {
        return builder()
            .addressRow1("Tuleva, Telliskivi 60")
            .addressRow2("TALLINN")
            .addressRow3("TALLINN")
            .country("EE")
            .postalIndex("10412")
            .districtCode("0784")
            .contactPreference(ContactPreferenceType.valueOf("E"))
            .languagePreference(LanguagePreferenceType.valueOf("EST"))
            .noticeNeeded("Y")
            .email("tuleva@tuleva.ee")
            .build();
    }

}
