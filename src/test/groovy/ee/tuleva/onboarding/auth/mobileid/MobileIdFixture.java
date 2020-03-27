package ee.tuleva.onboarding.auth.mobileid;

import ee.sk.mid.MidAuthenticationIdentity;
import ee.sk.mid.MidAuthenticationResult;

public class MobileIdFixture {

  public static String samplePhoneNumber = "5532522";
  public static String sampleLongPhoneNumber = "+3725532522";
  public static String sampleLithuanianPhoneNumber = "86532522";
  public static String sampleLongLithuanianPhoneNumber = "+37086532522";
  public static String sampleIdCode = "38812121215";
  public static String sampleSessionId = "12345";
  public static MobileIDSession sampleMobileIdSession =
      new MobileIDSession(sampleSessionId, "challenge", null, samplePhoneNumber);

  public static MidAuthenticationResult getSampleMidAuthResult(boolean isValid) {
    MidAuthenticationResult sampleAuthResult = new MidAuthenticationResult();
    MidAuthenticationIdentity sampleAuthIdentity = new MidAuthenticationIdentity();
    sampleAuthIdentity.setGivenName("Teet");
    sampleAuthIdentity.setSurName("Pauskar");
    sampleAuthIdentity.setIdentityCode(sampleIdCode);
    sampleAuthResult.setAuthenticationIdentity(sampleAuthIdentity);
    sampleAuthResult.setValid(isValid);
    return sampleAuthResult;
  }
}
