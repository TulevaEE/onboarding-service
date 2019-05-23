package ee.tuleva.onboarding.epis.contact;

import lombok.*;

import static ee.tuleva.onboarding.epis.contact.UserPreferences.ContactPreferenceType.*;
import static ee.tuleva.onboarding.epis.contact.UserPreferences.LanguagePreferenceType.*;

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

    @Builder.Default
    private ContactPreferenceType contactPreference = E;

    private String districtCode;

    private String addressRow1;

    private String addressRow2;

    private String addressRow3;

    private String postalIndex;

    @Builder.Default
    private String country = "EE";

    public enum LanguagePreferenceType {EST, RUS, ENG}

    @Builder.Default
    private LanguagePreferenceType languagePreference = EST;

    @Builder.Default
    private String noticeNeeded = "Y"; // boolean { 'Y', 'N' }

    private String email;

    private String phoneNumber;

    private String pensionAccountNumber;

}
