package ee.tuleva.onboarding.auth.smartid;

import ee.sk.smartid.SmartIdAuthenticationResult;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.io.Serializable;

@Data
@RequiredArgsConstructor
public class SmartIdSession implements Serializable {
    public final String verificationCode;
    public SmartIdAuthenticationResult authenticationResult;
}
