package ee.tuleva.onboarding.auth.smartid;

import ee.sk.smartid.AuthenticationIdentity;
import ee.sk.smartid.SmartIdAuthenticationResult;

public class SmartIdFixture {

    public static String identityCode = "10101010005";
    public static String givenName = "Aadu";
    public static String surName = "Kadakas";
    public static SmartIdSession sampleSmartIdSession = new SmartIdSession("12345");
    public static SmartIdSession sampleFinalSmartIdSession = new SmartIdSession("12345");
    public static SmartIdSession sampleFinalSmartIdSessionWithErrors = new SmartIdSession("12345");

    static {
        SmartIdAuthenticationResult result = new SmartIdAuthenticationResult();
        result.setValid(true);
        AuthenticationIdentity identity = new AuthenticationIdentity();
        identity.setIdentityCode(identityCode);
        identity.setGivenName(givenName);
        identity.setSurName(surName);
        result.setAuthenticationIdentity(identity);
        sampleFinalSmartIdSession.setAuthenticationResult(result);

        SmartIdAuthenticationResult result2 = new SmartIdAuthenticationResult();
        result2.setValid(false);
        result2.addError(SmartIdAuthenticationResult.Error.CERTIFICATE_EXPIRED);
        sampleFinalSmartIdSessionWithErrors.setAuthenticationResult(result2);
    }
}
