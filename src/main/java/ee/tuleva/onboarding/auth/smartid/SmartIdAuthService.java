package ee.tuleva.onboarding.auth.smartid;

import ee.sk.smartid.*;
import ee.sk.smartid.exception.*;
import ee.sk.smartid.rest.SmartIdConnector;
import ee.sk.smartid.rest.dao.NationalIdentity;
import ee.sk.smartid.rest.dao.SessionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static ee.tuleva.onboarding.error.response.ErrorsResponse.ofSingleError;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmartIdAuthService {
    private final SmartIdClient smartIdClient;
    private final SmartIdAuthenticationHashGenerator hashGenerator;
    private final AuthenticationResponseValidator authenticationResponseValidator;
    private final SmartIdConnector connector;

    public SmartIdSession startLogin(String personalCode) {
        try {
            AuthenticationHash authenticationHash = hashGenerator.generateHash();
            String verificationCode = authenticationHash.calculateVerificationCode();
            String sessionId = builder(personalCode, authenticationHash).initiateAuthentication();

            return new SmartIdSession(verificationCode, sessionId, personalCode, authenticationHash);
        } catch(UserAccountNotFoundException e) {
            log.info("Smart ID User account not found: " + personalCode, e);
            throw new SmartIdException(ofSingleError("smart.id.account.not.found", "Smart ID user account not found"));
        }
    }

    public boolean isLoginComplete(SmartIdSession smartIdSession) {
        try {
            SessionStatus sessionStatus = connector.getSessionStatus(smartIdSession.getSessionId());

            if (sessionStatus == null || "RUNNING".equalsIgnoreCase(sessionStatus.getState())) {
                return false;
            }

            if ("COMPLETE".equalsIgnoreCase(sessionStatus.getState())) {
                SmartIdAuthenticationResponse authenticationResponse =
                    builder(smartIdSession.getPersonalCode(), smartIdSession.getAuthenticationHash())
                        .createSmartIdAuthenticationResponse(sessionStatus);
                SmartIdAuthenticationResult authenticationResult = getAuthenticationResult(authenticationResponse);
                smartIdSession.setAuthenticationResult(authenticationResult);
                return authenticationResult.isValid();
            }
        } catch (UserAccountNotFoundException e) {
            log.info("Smart ID User account not found: " + smartIdSession.getPersonalCode(), e);
            throw new SmartIdException(ofSingleError("smart.id.account.not.found", "Smart ID user account not found"));
        } catch (UserRefusedException e) {
            throw new SmartIdException(ofSingleError("smart.id.user.refused", "Smart ID User refused"));
        } catch (Exception e) {
            log.error("Smart ID technical error", e);
            throw new SmartIdException(ofSingleError("smart.id.technical.error", "Smart ID technical error"));
        } finally {
            log.info("Smart ID authentication ended");
        }
        throw new IllegalStateException("Cannot complete Smart ID login");
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
        log.info("Smart ID authentication response is valid {}", result.isValid());
        if (!result.isValid()) {
            result.getErrors().forEach(log::error);
            throw SmartIdException.ofErrors(result.getErrors());
        }
        return result;
    }
}
