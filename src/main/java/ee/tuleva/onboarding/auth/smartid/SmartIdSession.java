package ee.tuleva.onboarding.auth.smartid;

import ee.sk.smartid.AuthenticationIdentity;
import ee.sk.smartid.SmartIdAuthenticationResult;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@RequiredArgsConstructor
public class SmartIdSession implements Serializable {
    public final String verificationCode;
    private boolean valid = false;
    private List<String> errors = new ArrayList<>();
    private String givenName;
    private String surName;
    private String identityCode;
    private String country;

    public void setAuthenticationResult(SmartIdAuthenticationResult result) {
        if (result.isValid()) {
            valid = true;
            AuthenticationIdentity identity = result.getAuthenticationIdentity();
            givenName = identity.getGivenName();
            surName = identity.getSurName();
            identityCode = identity.getIdentityCode();
            country = identity.getCountry();
        } else {
            valid = false;
            errors.addAll(result.getErrors());
        }
    }
}
