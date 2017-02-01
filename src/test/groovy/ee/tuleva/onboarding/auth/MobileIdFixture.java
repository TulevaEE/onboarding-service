package ee.tuleva.onboarding.auth;

import com.codeborne.security.mobileid.MobileIDSession;

public class MobileIdFixture {

    public static String samplePhoneNumber = "5532533";
    public static MobileIDSession sampleMobileIdSession = new MobileIDSession(
            123, "challenge", "firstName", "lastName", "personalCode"
    );

}
