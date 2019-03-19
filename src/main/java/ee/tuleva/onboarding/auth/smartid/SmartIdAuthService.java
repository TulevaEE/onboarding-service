package ee.tuleva.onboarding.auth.smartid;

import ee.sk.smartid.*;
import ee.sk.smartid.exception.ServerMaintenanceException;
import ee.sk.smartid.exception.TechnicalErrorException;
import ee.sk.smartid.exception.UserAccountNotFoundException;
import ee.sk.smartid.exception.UserRefusedException;
import ee.sk.smartid.rest.dao.NationalIdentity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmartIdAuthService {
    public final SmartIdClient smartIdClient;
    public final SmartIdAuthenticationHashGenerator hashGenerator;
    public final Executor smartIdExecutor;

    public SmartIdSession startLogin(String nationalIdentityCode) {
        NationalIdentity nationalIdentity = new NationalIdentity("EE", nationalIdentityCode);

        AuthenticationHash authenticationHash = hashGenerator.generateHash();

        String verificationCode = authenticationHash.calculateVerificationCode();

        SmartIdSession session = new SmartIdSession(verificationCode);

        smartIdExecutor.execute(() -> {
            try {
                log.info("Starting authentication");
                SmartIdAuthenticationResponse authenticationResponse = getSmartIdAuthenticationResponse(nationalIdentity, authenticationHash);

                SmartIdAuthenticationResult authenticationResult = getAuthenticationResult(authenticationResponse);
                session.setAuthenticationResult(authenticationResult);

            } catch (UserAccountNotFoundException e) {
                log.info("User account not found", e);
                session.setErrors(Collections.singletonList("User account not found"));
            } catch (UserRefusedException e) {
                log.info("User refused", e);
                session.setErrors(Collections.singletonList("User refused"));
            } catch (TechnicalErrorException | ServerMaintenanceException e) {
                log.info("Technical error", e);
                session.setErrors(Collections.singletonList("Smart ID technical error"));
            } finally {
                log.info("Authentication ended");
            }

        });

        return session;
    }

    SmartIdAuthenticationResponse getSmartIdAuthenticationResponse(NationalIdentity nationalIdentity,
                                                                   AuthenticationHash authenticationHash) {
        return smartIdClient
                .createAuthentication()
                .withNationalIdentity(nationalIdentity)
                .withAuthenticationHash(authenticationHash)
                .withCertificateLevel("QUALIFIED") // Certificate level can either be "QUALIFIED" or "ADVANCED"
                .authenticate();
    }

    SmartIdAuthenticationResult getAuthenticationResult(SmartIdAuthenticationResponse authenticationResponse) {
        AuthenticationResponseValidator authenticationResponseValidator = new AuthenticationResponseValidator();
        SmartIdAuthenticationResult authenticationResult =
                authenticationResponseValidator.validate(authenticationResponse);
        log.info("Response is valid {}", authenticationResult.isValid());
        if (!authenticationResult.getErrors().isEmpty()) {
            authenticationResult.getErrors().forEach(log::error);
        }
        return authenticationResult;
    }

    public boolean isLoginComplete(SmartIdSession smartIdSession) {
        if (!smartIdSession.getErrors().isEmpty()) {
            throw new IllegalStateException(String.join(",", smartIdSession.getErrors()));

        }
        return smartIdSession.isValid();
    }
}
