package ee.tuleva.onboarding.user;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * User preferences from CSD KPR registry.
 */
@Getter
@Setter
@Builder
public class UserPreferences {

    public enum ContactPreferenceType { //E - email, P - postal
        E, P
    }

    public enum LanguagePreferenceType {
        EST, RUS, ENG
    }

    private ContactPreferenceType contactPreference;

    private String districtCode;

    private String addressRow1;

    private String addressRow2;

    private String addressRow3;

    private String postalIndex;

    private String country;

    private LanguagePreferenceType languagePreference;

    private Integer noticeNeeded; //enum { '0', '1' }

    public static UserPreferences defaultUserPreferences() {
        return UserPreferences.builder()
                .addressRow1("Tuleva, Telliskivi 60")
                .addressRow2("TALLINN")
                .addressRow3("TALLINN")
                .country("EE")
                .postalIndex("10412")
                .districtCode("0784")
                .contactPreference(UserPreferences.ContactPreferenceType.valueOf("E"))
                .languagePreference(
                        (UserPreferences.LanguagePreferenceType.valueOf(
                                "EST")
                        ))
                .noticeNeeded(1)
                .build();
    }

}
