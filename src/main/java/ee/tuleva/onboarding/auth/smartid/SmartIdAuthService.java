package ee.tuleva.onboarding.auth.smartid;

import ee.sk.smartid.*;
import ee.sk.smartid.rest.dao.NationalIdentity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
            log.info("Starting authentication");
            SmartIdAuthenticationResponse authenticationResponse = smartIdClient
                    .createAuthentication()
                    .withNationalIdentity(nationalIdentity)
                    .withAuthenticationHash(authenticationHash)
                    .withCertificateLevel("QUALIFIED") // Certificate level can either be "QUALIFIED" or "ADVANCED"
                    .authenticate();
            log.info("Authentication ended");

            AuthenticationResponseValidator authenticationResponseValidator = new AuthenticationResponseValidator();
            SmartIdAuthenticationResult authenticationResult =
                    authenticationResponseValidator.validate(authenticationResponse);
            log.info("Response is valid {}", authenticationResult.isValid());
            if (!authenticationResult.getErrors().isEmpty()) {
                authenticationResult.getErrors().forEach(log::error);
            }
            session.setAuthenticationResult(authenticationResult);

        });

        return session;
    }

    public boolean isLoginComplete(SmartIdSession smartIdSession) {
        SmartIdAuthenticationResult authenticationResult = smartIdSession.authenticationResult;
        if (authenticationResult == null) {
            return false;
        }
        if (!authenticationResult.getErrors().isEmpty()) {
            throw new IllegalStateException(String.join(",", smartIdSession.getAuthenticationResult().getErrors()));

        }
        return authenticationResult.isValid();
    }
}
