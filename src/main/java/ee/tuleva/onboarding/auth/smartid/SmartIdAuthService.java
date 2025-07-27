package ee.tuleva.onboarding.auth.smartid;

import static ee.tuleva.onboarding.error.response.ErrorsResponse.ofSingleError;
import static java.util.Collections.*;
import static java.util.concurrent.Executors.*;

import ee.sk.smartid.*;
import ee.sk.smartid.exception.UnprocessableSmartIdResponseException;
import ee.sk.smartid.exception.useraccount.UserAccountNotFoundException;
import ee.sk.smartid.exception.useraction.UserRefusedException;
import ee.sk.smartid.rest.dao.Interaction;
import ee.sk.smartid.rest.dao.SemanticsIdentifier;
import ee.sk.smartid.rest.dao.SemanticsIdentifier.CountryCode;
import ee.sk.smartid.rest.dao.SemanticsIdentifier.IdentityType;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.map.LRUMap;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmartIdAuthService {

  @Builder
  @Data
  static class SmartIdResult {

    private AuthenticationIdentity authenticationIdentity;
    private SmartIdException error;
    private Instant createdAt;
  }

  private static final Duration TTL = Duration.ofSeconds(60);

  private final Map<String, SmartIdResult> smartIdResults = synchronizedMap(new LRUMap<>());
  private final ExecutorService poller = newFixedThreadPool(20);

  private final SmartIdClient smartIdClient;
  private final SmartIdAuthenticationHashGenerator hashGenerator;
  private final AuthenticationResponseValidator authenticationResponseValidator;
  private final Clock clock;

  @SneakyThrows
  @PreDestroy
  public void stop() {
    poller.shutdown();
    try {
      if (!poller.awaitTermination(800, TimeUnit.MILLISECONDS)) {
        poller.shutdownNow();
      }
    } catch (InterruptedException e) {
      poller.shutdownNow();
    }
  }

  public SmartIdSession startLogin(String personalCode) {
    var authenticationHash = hashGenerator.generateHash();
    String verificationCode = authenticationHash.calculateVerificationCode();
    SmartIdSession session = new SmartIdSession(verificationCode, personalCode, authenticationHash);
    poll(session);
    return session;
  }

  public Optional<AuthenticationIdentity> getAuthenticationIdentity(String authenticationHash) {
    var result = smartIdResults.get(authenticationHash);
    if (result == null) {
      return Optional.empty();
    }
    if (Instant.now(clock).isAfter(result.getCreatedAt().plus(TTL))) {
      smartIdResults.remove(authenticationHash);
      return Optional.empty();
    }
    if (result.error != null) {
      throw result.error;
    }
    return Optional.ofNullable(result.getAuthenticationIdentity());
  }

  private void poll(SmartIdSession session) {
    log.info("Starting to poll");
    poller.submit(
        () -> {
          final var resultBuilder = SmartIdResult.builder().createdAt(Instant.now(clock));
          try {
            SmartIdAuthenticationResponse response =
                requestBuilder(session.getPersonalCode(), session.getAuthenticationHash())
                    .authenticate();
            AuthenticationIdentity authenticationIdentity =
                authenticationResponseValidator.validate(response);
            resultBuilder.authenticationIdentity(authenticationIdentity);
          } catch (UnprocessableSmartIdResponseException e) {
            log.info("Smart ID validation failed: personalCode=" + session.getPersonalCode(), e);
            resultBuilder.error(
                new SmartIdException(
                    ofSingleError("smart.id.validation.failed", "Smart ID validation failed")));
          } catch (UserAccountNotFoundException e) {
            log.info(
                "Smart ID User account not found: personalCode=" + session.getPersonalCode(), e);
            resultBuilder.error(
                new SmartIdException(
                    ofSingleError(
                        "smart.id.account.not.found", "Smart ID user account not found")));
          } catch (UserRefusedException e) {
            resultBuilder.error(
                new SmartIdException(
                    ofSingleError("smart.id.user.refused", "Smart ID User refused")));
          } catch (Exception e) {
            log.error("Smart ID technical error", e);
            resultBuilder.error(
                new SmartIdException(
                    ofSingleError("smart.id.technical.error", "Smart ID technical error")));
          } finally {
            log.info("Smart ID authentication ended");
            smartIdResults.put(
                session.getAuthenticationHash().getHashInBase64(), resultBuilder.build());
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
