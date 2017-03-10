package ee.tuleva.onboarding.user;

import ee.eesti.xtee6.kpr.PersonDataResponseType;
import ee.tuleva.onboarding.kpr.KPRClient;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class CsdUserPreferencesService {

    private final KPRClient kprClient;

    public UserPreferences getPreferences(String idcode) {
        PersonDataResponseType csdPersonData = kprClient.personData(idcode);

        return UserPreferences.builder()
                .addressRow1(csdPersonData.getContactData().getAddressRow1())
                .addressRow2(csdPersonData.getContactData().getAddressRow2())
                .addressRow3(csdPersonData.getContactData().getAddressRow3())
                .country(csdPersonData.getContactData().getCountry().value())
                .postalIndex(csdPersonData.getContactData().getPostalIndex())
                .districtCode(csdPersonData.getContactData().getDistrictCode())
                .contactPreference(UserPreferences.ContactPreferenceType.valueOf(csdPersonData.getContactData().getContactPreference().value()))
                .languagePreference(
                        (UserPreferences.LanguagePreferenceType.valueOf(
                                csdPersonData.getIndividual().getLanguagePreference().value())
                        ))
                .noticeNeeded(Integer.valueOf(csdPersonData.getIndividual().getExtractFlag()))
                .build();
    }
}
