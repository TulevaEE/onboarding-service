package ee.tuleva.onboarding.user;

import ee.tuleva.onboarding.auth.GrantType;

public enum NameChangeSource {
  ID_CARD_LOGIN,
  MOBILE_ID_LOGIN,
  SMART_ID_LOGIN,
  PARTNER_LOGIN,
  POPULATION_REGISTER,
  ADMIN;

  static NameChangeSource loginWith(GrantType grantType) {
    return switch (grantType) {
      case ID_CARD -> ID_CARD_LOGIN;
      case MOBILE_ID -> MOBILE_ID_LOGIN;
      case SMART_ID -> SMART_ID_LOGIN;
      case PARTNER -> PARTNER_LOGIN;
    };
  }
}
