package ee.tuleva.onboarding.user

import ee.eesti.xtee6.kpr.ContactPreferenceType
import ee.eesti.xtee6.kpr.CountryType
import ee.eesti.xtee6.kpr.PersonDataResponseType
import ee.tuleva.onboarding.kpr.KPRClient
import spock.lang.Specification

class PreferencesServiceSpec extends Specification {


    def "preferences service works"() {
        given:
        KPRClient kprClient = Mock(KPRClient)
        PreferencesService preferencesService = new PreferencesService(kprClient)

        PersonDataResponseType personDataResponseType = new PersonDataResponseType()
        personDataResponseType.setContactData(new PersonDataResponseType.ContactData())
        personDataResponseType.getContactData().setDistrictCode("007")
        personDataResponseType.getContactData().setCountry(CountryType.LV)
        personDataResponseType.getContactData().setContactPreference(ContactPreferenceType.E)

        when:
        1 * kprClient.personData("123") >> personDataResponseType

        then:
        preferencesService.getPreferences("123").districtCode == "007"
    }

}
