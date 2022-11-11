package ee.tuleva.onboarding.auth.mobileid;

import ee.sk.mid.MidAuthenticationIdentity;
import ee.sk.mid.MidAuthenticationResult;
import ee.sk.mid.MidHashToSign;
import ee.sk.mid.MidHashType;
import ee.sk.mid.rest.dao.MidSessionStatus;
import ee.sk.smartid.AuthenticationHash;

public class MobileIdFixture {

  public static String samplePhoneNumber = "5532522";
  public static String sampleLongPhoneNumber = "+3725532522";
  public static String sampleLithuanianPhoneNumber = "86532522";
  public static String sampleLongLithuanianPhoneNumber = "+37086532522";
  public static String sampleIdCode = "38812121215";
  public static String sampleSessionId = "12345";

  public static MidHashToSign hash = MidHashToSign.newBuilder()
      .withHashType(MidHashType.SHA256)
      .withDataToHash("data".getBytes())
      .build();
  public static MobileIDSession sampleMobileIdSession =
      new MobileIDSession(sampleSessionId, "challenge", hash, samplePhoneNumber);

  public static MidSessionStatus getSampleMidSessionComplete() {
    MidSessionStatus status = new MidSessionStatus();
    status.setState("COMPLETE");
    return status;
  }

  public static MidSessionStatus getSampleMidSessionIncomplete() {
    MidSessionStatus status = new MidSessionStatus();
    status.setState("RUNNING");
    return status;
  }

  public static MidSessionStatus getSampleMidSessionOther() {
    MidSessionStatus status = new MidSessionStatus();
    status.setState("");
    return status;
  }

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
