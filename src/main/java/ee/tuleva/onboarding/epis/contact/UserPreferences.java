package ee.tuleva.onboarding.epis.contact;

import ee.tuleva.onboarding.user.address.Address;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

import static ee.tuleva.onboarding.epis.contact.UserPreferences.ContactPreferenceType.E;
import static ee.tuleva.onboarding.epis.contact.UserPreferences.LanguagePreferenceType.EST;

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

    private List<Distribution> thirdPillarDistribution;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Distribution {
        private String activeThirdPillarFundIsin;
        private BigDecimal percentage;
    }

    private String activeSecondPillarFundIsin;

    private boolean isSecondPillarActive;

    private boolean isThirdPillarActive;

    public Address getAddress() {
        return Address.builder()
            .street(addressRow1)
            .countryCode(country)
            .postalCode(postalIndex)
            .districtCode(districtCode)
            .build();
    }

    public UserPreferences setAddress(Address address) {
        addressRow1 = address.getStreet();
        country = address.getCountryCode();
        districtCode = address.getDistrictCode();
        postalIndex = address.getPostalCode();
        return this;
    }

}
