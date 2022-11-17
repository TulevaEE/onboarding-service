package ee.tuleva.onboarding.auth;

import org.springframework.security.oauth2.core.AuthorizationGrantType;

public class GrantType {

  public static final AuthorizationGrantType SMART_ID = new AuthorizationGrantType("smart_id");
  public static final AuthorizationGrantType MOBILE_ID = new AuthorizationGrantType("mobile_id");
  public static final AuthorizationGrantType ID_CARD = new AuthorizationGrantType("id_card");
}
