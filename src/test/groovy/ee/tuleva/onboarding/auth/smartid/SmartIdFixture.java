package ee.tuleva.onboarding.auth.smartid;

import ee.sk.smartid.AuthenticationHash;
import ee.sk.smartid.AuthenticationIdentity;
import ee.sk.smartid.SmartIdAuthenticationResponse;

public class SmartIdFixture {

  public static final String personalCode = "38501010002";
  public static final String firstName = "Aadu";
  public static final String lastName = "Kadakas";
  private static final String verificationCode = "12345";
  public static SmartIdSession sampleSmartIdSession =
      new SmartIdSession(
          verificationCode, new SmartIdAuthenticationResponse(), personalCode, AuthenticationHash.generateRandomHash());
  public static SmartIdSession sampleFinalSmartIdSession =
      new SmartIdSession(
          verificationCode, new SmartIdAuthenticationResponse(), personalCode, AuthenticationHash.generateRandomHash());

  public static AuthenticationIdentity sampleAuthenticationIdentity = new AuthenticationIdentity();

  static {
    sampleAuthenticationIdentity.setIdentityCode(personalCode);
    sampleAuthenticationIdentity.setGivenName(firstName);
    sampleAuthenticationIdentity.setSurname(lastName);
    sampleFinalSmartIdSession.setAuthenticationIdentity(sampleAuthenticationIdentity);
  }
}
