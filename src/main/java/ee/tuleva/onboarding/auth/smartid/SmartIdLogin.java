package ee.tuleva.onboarding.auth.smartid;

import java.io.Serializable;

public sealed interface SmartIdLogin extends Serializable
    permits DeviceLinkLogin, NotificationLogin {

  String sessionId();
}
