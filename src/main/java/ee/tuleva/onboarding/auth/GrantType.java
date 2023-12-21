package ee.tuleva.onboarding.auth;

public enum GrantType {
  ID_CARD,
  MOBILE_ID,
  SMART_ID,
  PARTNER;

  public static final String GRANT_TYPE = "grantType";
}
