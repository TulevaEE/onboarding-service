package ee.tuleva.onboarding.auth.smartid;

import ee.sk.smartid.AuthenticationHash;
import ee.sk.smartid.AuthenticationIdentity;
import ee.sk.smartid.SmartIdAuthenticationResult;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@RequiredArgsConstructor
@Slf4j
public class SmartIdSession implements Serializable {

    private static final long serialVersionUID = 6407589354898164171L;

    private final String verificationCode;
    private final String sessionId;
    private final String identityCode;
    private final AuthenticationHash authenticationHash;
    private boolean valid = false; // TODO: remove result from session
    private String givenName;
    private String surName;
    private String country;

    // TODO: remove errors from session
    private List<String> errors = new ArrayList<>();

    public void setAuthenticationResult(SmartIdAuthenticationResult result) {
        if (result.isValid()) {
            valid = true;
            AuthenticationIdentity identity = result.getAuthenticationIdentity();
            givenName = identity.getGivenName();
            surName = identity.getSurName();
            country = identity.getCountry();
        } else {
            valid = false;
            errors.addAll(result.getErrors());
            log.info("SmartID errors: {}", errors);
        }
    }
}
