package ee.tuleva.onboarding.epis;

import ee.tuleva.onboarding.mandate.MandateContactDetails;
import ee.tuleva.onboarding.mandate.MandateContactDetails.LanguagePreference;

class ContactDetailsMapper {

  static MandateContactDetails toMandateContactDetails(ContactDetails contactDetails) {
    return MandateContactDetails.builder()
        .email(contactDetails.getEmail())
        .address(contactDetails.getAddress())
        .secondPillarActive(contactDetails.isSecondPillarActive())
        .thirdPillarActive(contactDetails.isThirdPillarActive())
        .noticeNeeded(contactDetails.getNoticeNeeded())
        .languagePreference(
            switch (contactDetails.getLanguagePreference()) {
              case EST -> LanguagePreference.EST;
              case RUS -> LanguagePreference.RUS;
              case ENG -> LanguagePreference.ENG;
            })
        .build();
  }
}
