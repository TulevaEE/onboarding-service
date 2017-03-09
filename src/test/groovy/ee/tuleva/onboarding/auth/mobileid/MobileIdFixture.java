package ee.tuleva.onboarding.auth.mobileid;

import com.codeborne.security.mobileid.MobileIDSession;

public class MobileIdFixture {

    public static String samplePhoneNumber = "5532522";
    public static MobileIDSession sampleMobileIdSession = new MobileIDSession(
            123, "challenge", "Jordan", "Valdma", "38812121212", samplePhoneNumber
    );

}
