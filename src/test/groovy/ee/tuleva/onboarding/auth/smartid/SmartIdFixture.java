package ee.tuleva.onboarding.auth.smartid;

import ee.sk.smartid.AuthenticationHash;
import ee.sk.smartid.AuthenticationIdentity;
import ee.sk.smartid.SmartIdAuthenticationResult;

public class SmartIdFixture {

    public static final String identityCode = "10101010005";
    public static final String givenName = "Aadu";
    public static final String surName = "Kadakas";
    public static final String sessionId = "sessionId123";
    private static final String verificationCode = "12345";
    public static SmartIdSession sampleSmartIdSession = new SmartIdSession(verificationCode, sessionId, identityCode,
        AuthenticationHash.generateRandomHash());
    public static SmartIdSession sampleFinalSmartIdSession = new SmartIdSession(verificationCode, sessionId, identityCode,
        AuthenticationHash.generateRandomHash());

    static {
        AuthenticationIdentity identity = new AuthenticationIdentity();
        identity.setIdentityCode(identityCode);
        identity.setGivenName(givenName);
        identity.setSurName(surName);

        SmartIdAuthenticationResult result = new SmartIdAuthenticationResult();
        result.setValid(true);
        result.setAuthenticationIdentity(identity);

        sampleFinalSmartIdSession.setAuthenticationResult(result);
    }

}
