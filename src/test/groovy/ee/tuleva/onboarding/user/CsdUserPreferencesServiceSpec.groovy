package ee.tuleva.onboarding.user

import ee.eesti.xtee6.kpr.ContactPreferenceType
import ee.eesti.xtee6.kpr.CountryType
import ee.eesti.xtee6.kpr.LangType
import ee.eesti.xtee6.kpr.PersonDataResponseType
import ee.tuleva.onboarding.kpr.KPRClient
import spock.lang.Specification

class CsdUserPreferencesServiceSpec extends Specification {


    def "getPreferences: get preferences for id code"() {
        given:
        KPRClient kprClient = Mock(KPRClient)
        CsdUserPreferencesService preferencesService = new CsdUserPreferencesService(kprClient)

        PersonDataResponseType personDataResponseType = new PersonDataResponseType()
        personDataResponseType.setContactData(new PersonDataResponseType.ContactData())
        personDataResponseType.getContactData().setDistrictCode("007")
        personDataResponseType.getContactData().setCountry(CountryType.LV)
        personDataResponseType.getContactData().setContactPreference(ContactPreferenceType.E)

        personDataResponseType.setIndividual(new PersonDataResponseType.Individual())
        personDataResponseType.getIndividual().setLanguagePreference(LangType.EST)
        personDataResponseType.getIndividual().setExtractFlag("1".toString())

        1 * kprClient.personData("123") >> personDataResponseType

        expect:
        preferencesService.getPreferences("123").districtCode == "007"
    }

}
