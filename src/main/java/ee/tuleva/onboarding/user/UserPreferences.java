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

    enum ContactPreferenceType {
        E, P
    }

    private ContactPreferenceType contactPreference;

    private String districtCode;

    private String addressRow1;

    private String addressRow2;

    private String addressRow3;

    private String postalIndex;

    private String country;
}
