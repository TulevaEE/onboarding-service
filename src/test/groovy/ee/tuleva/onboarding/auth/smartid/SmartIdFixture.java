package ee.tuleva.onboarding.auth.smartid;

import ee.sk.smartid.AuthenticationHash;
import ee.sk.smartid.AuthenticationIdentity;
import ee.sk.smartid.SmartIdAuthenticationResult;

public class SmartIdFixture {

    public static final String personalCode = "38501010002";
    public static final String firstName = "Aadu";
    public static final String lastName = "Kadakas";
    public static final String sessionId = "sessionId123";
    private static final String verificationCode = "12345";
    public static SmartIdSession sampleSmartIdSession = new SmartIdSession(verificationCode, sessionId, personalCode,
        AuthenticationHash.generateRandomHash());
    public static SmartIdSession sampleFinalSmartIdSession = new SmartIdSession(verificationCode, sessionId, personalCode,
        AuthenticationHash.generateRandomHash());

    static {
        AuthenticationIdentity identity = new AuthenticationIdentity();
        identity.setIdentityCode(personalCode);
        identity.setGivenName(firstName);
        identity.setSurName(lastName);

        SmartIdAuthenticationResult result = new SmartIdAuthenticationResult();
        result.setValid(true);
        result.setAuthenticationIdentity(identity);

        sampleFinalSmartIdSession.setAuthenticationResult(result);
    }

}
