package ee.tuleva.onboarding.auth.smartid;

import ee.sk.smartid.*;
import ee.sk.smartid.exception.UserAccountNotFoundException;
import ee.sk.smartid.exception.UserRefusedException;
import ee.sk.smartid.rest.SmartIdConnector;
import ee.sk.smartid.rest.dao.NationalIdentity;
import ee.sk.smartid.rest.dao.SessionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmartIdAuthService {
    private final SmartIdClient smartIdClient;
    private final SmartIdAuthenticationHashGenerator hashGenerator;
    private final AuthenticationResponseValidator authenticationResponseValidator;
    private final SmartIdConnector connector;

    public SmartIdSession startLogin(String nationalIdentityCode) {
        AuthenticationHash authenticationHash = hashGenerator.generateHash();
        String verificationCode = authenticationHash.calculateVerificationCode();
        String sessionId = builder(nationalIdentityCode, authenticationHash).initiateAuthentication();

        return new SmartIdSession(verificationCode, sessionId, nationalIdentityCode, authenticationHash);
    }

    public boolean isLoginComplete(SmartIdSession smartIdSession) {
        try {
            SessionStatus sessionStatus = connector.getSessionStatus(smartIdSession.getSessionId());

            if (sessionStatus == null || "RUNNING".equalsIgnoreCase(sessionStatus.getState())) {
                return false;
            }

            if ("COMPLETE".equalsIgnoreCase(sessionStatus.getState())) {
                SmartIdAuthenticationResponse authenticationResponse =
                    builder(smartIdSession.getIdentityCode(), smartIdSession.getAuthenticationHash())
                        .createSmartIdAuthenticationResponse(sessionStatus);
                SmartIdAuthenticationResult authenticationResult = getAuthenticationResult(authenticationResponse);
                smartIdSession.setAuthenticationResult(authenticationResult);
            }
        } catch (UserAccountNotFoundException e) {
            log.info("User account not found", e);
            smartIdSession.setErrors(Collections.singletonList("User account not found"));
        } catch (UserRefusedException e) {
            log.info("User refused", e);
            smartIdSession.setErrors(Collections.singletonList("User refused"));
        } catch (Exception e) {
            log.error("Technical error", e);
            smartIdSession.setErrors(Collections.singletonList("Smart ID technical error"));
        } finally {
            log.info("Authentication ended");
        }
        if (!smartIdSession.getErrors().isEmpty()) {
            throw new IllegalStateException(String.join(",", smartIdSession.getErrors()));
        }
        return smartIdSession.isValid();
    }

    private AuthenticationRequestBuilder builder(String nationalIdentityCode, AuthenticationHash authenticationHash) {
        return smartIdClient
            .createAuthentication()
            .withNationalIdentity(new NationalIdentity("EE", nationalIdentityCode))
            .withAuthenticationHash(authenticationHash)
            .withCertificateLevel("QUALIFIED"); // Certificate level can either be "QUALIFIED" or "ADVANCED"
    }

    private SmartIdAuthenticationResult getAuthenticationResult(SmartIdAuthenticationResponse authenticationResponse) {
        SmartIdAuthenticationResult result = authenticationResponseValidator.validate(authenticationResponse);
        log.info("Response is valid {}", result.isValid());
        if (!result.getErrors().isEmpty()) {
            result.getErrors().forEach(log::error);
        }
        return result;
    }
}
