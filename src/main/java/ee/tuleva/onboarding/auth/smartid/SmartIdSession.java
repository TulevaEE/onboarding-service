package ee.tuleva.onboarding.auth.smartid;

import ee.sk.smartid.AuthenticationHash;
import ee.sk.smartid.AuthenticationIdentity;
import ee.tuleva.onboarding.auth.principal.Person;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

@Data
public class SmartIdSession implements Person, Serializable {

  @Serial private static final long serialVersionUID = 6326478770346040900L;

  private final String verificationCode;
  private final String sessionId;
  private final String personalCode;
  private final AuthenticationHash authenticationHash;
  private String firstName;
  private String lastName;
  private String country;

  public void setAuthenticationIdentity(AuthenticationIdentity identity) {
    firstName = identity.getGivenName();
    lastName = identity.getSurname();
    country = identity.getCountry();
  }

  @Override
  public String getPhoneNumber() {
    return null;
  }
}
