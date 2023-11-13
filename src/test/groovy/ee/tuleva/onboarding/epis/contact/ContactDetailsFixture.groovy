package ee.tuleva.onboarding.epis.contact

import static ee.tuleva.onboarding.epis.contact.ContactDetails.*

class ContactDetailsFixture {

  static ContactDetails contactDetailsFixture() {
    return builder()
        .firstName("Erko")
        .lastName("Risthein")
        .personalCode("38501010002")
        .country("EE")
        .languagePreference(LanguagePreferenceType.valueOf("EST"))
        .noticeNeeded("Y")
        .email("tuleva@tuleva.ee")
        .phoneNumber("+372546545")
        .pensionAccountNumber("993432432")
        .thirdPillarDistribution([new Distribution("EE123", 1.0)])
        .isSecondPillarActive(true)
        .isThirdPillarActive(true)
        .build()
  }
}
