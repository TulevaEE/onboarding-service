package ee.tuleva.onboarding.auth.smartid;

import static ee.tuleva.onboarding.error.response.ErrorsResponse.ofSingleError;

import ee.sk.smartid.AuthenticationHash;
import ee.sk.smartid.AuthenticationIdentity;
import ee.sk.smartid.AuthenticationRequestBuilder;
import ee.sk.smartid.AuthenticationResponseValidator;
import ee.sk.smartid.SmartIdAuthenticationResponse;
import ee.sk.smartid.SmartIdClient;
import ee.sk.smartid.exception.UnprocessableSmartIdResponseException;
import ee.sk.smartid.exception.useraccount.UserAccountNotFoundException;
import ee.sk.smartid.exception.useraction.UserRefusedException;
import ee.sk.smartid.rest.SmartIdConnector;
import ee.sk.smartid.rest.dao.Interaction;
import ee.sk.smartid.rest.dao.SemanticsIdentifier;
import ee.sk.smartid.rest.dao.SemanticsIdentifier.CountryCode;
import ee.sk.smartid.rest.dao.SemanticsIdentifier.IdentityType;
import ee.sk.smartid.rest.dao.SessionStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
    } catch (UserAccountNotFoundException e) {
      log.info("Smart ID User account not found: personalCode=" + personalCode, e);
      throw new SmartIdException(
          ofSingleError("smart.id.account.not.found", "Smart ID user account not found"));
    }
  }

  public boolean isLoginComplete(SmartIdSession session) {
    try {
      SessionStatus sessionStatus = connector.getSessionStatus(session.getSessionId());

      if (sessionStatus == null || "RUNNING".equalsIgnoreCase(sessionStatus.getState())) {
        return false;
      }

      if ("COMPLETE".equalsIgnoreCase(sessionStatus.getState())) {
        SmartIdAuthenticationResponse authenticationResponse =
            builder(session.getPersonalCode(), session.getAuthenticationHash())
                .createSmartIdAuthenticationResponse(sessionStatus);
        AuthenticationIdentity authenticationIdentity =
            authenticationResponseValidator.validate(authenticationResponse);
        session.setAuthenticationIdentity(authenticationIdentity);
        return true;
      }
    } catch (UnprocessableSmartIdResponseException e) {
      log.info("Smart ID validation failed: personalCode=" + session.getPersonalCode(), e);
      throw new SmartIdException(
          ofSingleError("smart.id.validation.failed", "Smart ID validation failed"));
    } catch (UserAccountNotFoundException e) {
      log.info("Smart ID User account not found: personalCode=" + session.getPersonalCode(), e);
      throw new SmartIdException(
          ofSingleError("smart.id.account.not.found", "Smart ID user account not found"));
    } catch (UserRefusedException e) {
      throw new SmartIdException(ofSingleError("smart.id.user.refused", "Smart ID User refused"));
    } catch (Exception e) {
      log.error("Smart ID technical error", e);
      throw new SmartIdException(
          ofSingleError("smart.id.technical.error", "Smart ID technical error"));
    } finally {
      log.info("Smart ID authentication ended");
    }
    throw new IllegalStateException("Cannot complete Smart ID login");
  }

  private AuthenticationRequestBuilder builder(
      String personalCode, AuthenticationHash authenticationHash) {
    return smartIdClient
        .createAuthentication()
        .withSemanticsIdentifier(
            new SemanticsIdentifier(IdentityType.PNO, CountryCode.EE, personalCode))
        .withAuthenticationHash(authenticationHash)
        .withAllowedInteractionsOrder(List.of(Interaction.displayTextAndPIN("Tuleva: Log In")))
        .withCertificateLevel("QUALIFIED");
  }
}
