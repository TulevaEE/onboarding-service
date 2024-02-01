package ee.tuleva.onboarding.auth.smartid;

import ee.sk.smartid.AuthenticationHash;
import ee.sk.smartid.AuthenticationIdentity;
import ee.sk.smartid.rest.dao.SemanticsIdentifier;
import ee.sk.smartid.rest.dao.SemanticsIdentifier.CountryCode;
import ee.sk.smartid.rest.dao.SemanticsIdentifier.IdentityType;

public class SmartIdFixture {

  public static final String personalCode = "38501010002";
  public static final String firstName = "Aadu";
  public static final String lastName = "Kadakas";
  private static final String verificationCode = "12345";
  public static final String aSessionId = "someSessionId";
  public static SmartIdSession sampleSmartIdSession =
      new SmartIdSession(verificationCode, personalCode, AuthenticationHash.generateRandomHash());
  public static SmartIdSession sampleFinalSmartIdSession =
      new SmartIdSession(verificationCode, personalCode, AuthenticationHash.generateRandomHash());

  public static AuthenticationIdentity anAuthenticationIdentity = new AuthenticationIdentity();
  public static SemanticsIdentifier aSemanticsIdentifier =
      new SemanticsIdentifier(IdentityType.PNO, CountryCode.EE, personalCode);

  static {
    anAuthenticationIdentity.setIdentityCode(personalCode);
    anAuthenticationIdentity.setGivenName(firstName);
    anAuthenticationIdentity.setSurname(lastName);
  }
}
