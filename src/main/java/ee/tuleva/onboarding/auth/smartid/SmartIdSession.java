package ee.tuleva.onboarding.auth.smartid;

import ee.sk.smartid.AuthenticationHash;
import ee.sk.smartid.AuthenticationIdentity;
import ee.sk.smartid.SmartIdAuthenticationResult;
import ee.tuleva.onboarding.auth.principal.Person;
import java.io.Serializable;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Data
@RequiredArgsConstructor
@Slf4j
public class SmartIdSession implements Person, Serializable {

  private static final long serialVersionUID = 6326478770346040900L;

  private final String verificationCode;
  private final String sessionId;
  private final String personalCode;
  private final AuthenticationHash authenticationHash;
  private String firstName;
  private String lastName;
  private String country;

  public void setAuthenticationResult(SmartIdAuthenticationResult result) {
    AuthenticationIdentity identity = result.getAuthenticationIdentity();
    firstName = identity.getGivenName();
    lastName = identity.getSurName();
    country = identity.getCountry();
  }
}
