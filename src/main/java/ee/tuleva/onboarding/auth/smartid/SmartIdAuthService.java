package ee.tuleva.onboarding.auth.smartid;

import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import ee.sk.smartid.AuthenticationHash;
import ee.sk.smartid.AuthenticationIdentity;
import ee.sk.smartid.AuthenticationRequestBuilder;
import ee.sk.smartid.AuthenticationResponseValidator;
import ee.sk.smartid.SmartIdAuthenticationResponse;
import ee.sk.smartid.SmartIdClient;
import ee.sk.smartid.exception.UnprocessableSmartIdResponseException;
import ee.sk.smartid.exception.useraccount.UserAccountNotFoundException;
import ee.sk.smartid.exception.useraction.UserRefusedException;
import ee.sk.smartid.rest.dao.Interaction;
import ee.sk.smartid.rest.dao.SemanticsIdentifier;
import ee.sk.smartid.rest.dao.SemanticsIdentifier.CountryCode;
import ee.sk.smartid.rest.dao.SemanticsIdentifier.IdentityType;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmartIdAuthService {

  private final ExecutorService poller = newVirtualThreadPerTaskExecutor();

  private final SmartIdClient smartIdClient;
  private final SmartIdAuthenticationHashGenerator hashGenerator;
  private final AuthenticationResponseValidator authenticationResponseValidator;
  private final GenericSessionStore genericSessionStore;
  private final Clock clock;

  @SneakyThrows
  @PreDestroy
  public void stop() {
    poller.shutdown();
    try {
      if (!poller.awaitTermination(800, MILLISECONDS)) {
        poller.shutdownNow();
      }
    } catch (InterruptedException e) {
      poller.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  public SmartIdSession startLogin(String personalCode, String httpSessionId) {
    var authenticationHash = hashGenerator.generateHash();
    var verificationCode = authenticationHash.calculateVerificationCode();
    var session =
        new SmartIdSession(verificationCode, personalCode, authenticationHash, Instant.now(clock));
    poll(session, httpSessionId);
    return session;
  }

  private void poll(SmartIdSession session, String httpSessionId) {
    log.info("Starting to poll");
    poller.submit(
        () -> {
          try {
            SmartIdAuthenticationResponse response =
                requestBuilder(session.getPersonalCode(), session.getAuthenticationHash())
                    .authenticate();
            AuthenticationIdentity identity = authenticationResponseValidator.validate(response);
            session.setPerson(new SmartIdPerson(identity));
          } catch (UnprocessableSmartIdResponseException e) {
            log.info("Smart ID validation failed: personalCode=" + session.getPersonalCode(), e);
            session.setErrorCode("smart.id.validation.failed");
            session.setErrorMessage("Smart ID validation failed");
          } catch (UserAccountNotFoundException e) {
            log.info(
                "Smart ID User account not found: personalCode=" + session.getPersonalCode(), e);
            session.setErrorCode("smart.id.account.not.found");
            session.setErrorMessage("Smart ID user account not found");
          } catch (UserRefusedException e) {
            session.setErrorCode("smart.id.user.refused");
            session.setErrorMessage("Smart ID User refused");
          } catch (Exception e) {
            log.error("Smart ID technical error", e);
            session.setErrorCode("smart.id.technical.error");
            session.setErrorMessage("Smart ID technical error");
          } finally {
            log.info("Smart ID authentication ended");
            genericSessionStore.saveBySessionId(httpSessionId, session);
          }
        });
  }

  private AuthenticationRequestBuilder requestBuilder(
      String personalCode, AuthenticationHash authenticationHash) {
    return smartIdClient
        .createAuthentication()
        .withSemanticsIdentifier(
            new SemanticsIdentifier(IdentityType.PNO, CountryCode.EE, personalCode))
        .withAuthenticationHash(authenticationHash)
        .withAllowedInteractionsOrder(
            List.of(
                Interaction.verificationCodeChoice("Log in to Tuleva?"),
                Interaction.displayTextAndPIN("Log in to Tuleva?")))
        .withCertificateLevel("QUALIFIED");
  }
}
