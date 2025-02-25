package ee.tuleva.onboarding.epis.contact

import java.time.Instant
import java.time.temporal.ChronoUnit

import static ee.tuleva.onboarding.epis.contact.ContactDetails.*
import static java.time.temporal.ChronoUnit.DAYS
import static java.time.temporal.ChronoUnit.MONTHS

class ContactDetailsFixture {

  static ContactDetails contactDetailsFixture() {
    return builder()
        .firstName("Erko")
        .lastName("Risthein")
        .personalCode("38501010002")
        .lastUpdateDate(Instant.parse("2019-10-01T12:13:27.141Z"))
        .country("EE")
        .languagePreference(LanguagePreferenceType.valueOf("EST"))
        .noticeNeeded("Y")
        .email("tuleva@tuleva.ee")
        .phoneNumber("+372546545")
        .pensionAccountNumber("993432432")
        .thirdPillarDistribution([new Distribution("EE123", 1.0)])
        .isSecondPillarActive(true)
        .isThirdPillarActive(true)
        .secondPillarOpenDate(Instant.parse("2019-10-01T12:13:27.141Z"))
        .thirdPillarInitDate(Instant.parse("2019-10-01T12:13:27.141Z"))
        .build()
  }

  static ContactDetails recentlyUpdatedContactDetailsFixture() {
    return builder()
        .firstName("Erko")
        .lastName("Risthein")
        .personalCode("38501010002")
        .lastUpdateDate(Instant.now().minus(30, DAYS))
        .country("EE")
        .languagePreference(LanguagePreferenceType.valueOf("EST"))
        .noticeNeeded("Y")
        .email("tuleva@tuleva.ee")
        .phoneNumber("+372546545")
        .pensionAccountNumber("993432432")
        .thirdPillarDistribution([new Distribution("EE123", 1.0)])
        .isSecondPillarActive(true)
        .isThirdPillarActive(true)
        .secondPillarOpenDate(Instant.parse("2019-10-01T12:13:27.141Z"))
        .thirdPillarInitDate(Instant.parse("2019-10-01T12:13:27.141Z"))
        .build()
  }
}
