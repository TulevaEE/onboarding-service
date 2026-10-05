package ee.tuleva.onboarding.auth.smartid;

import static ee.sk.smartid.FlowType.NOTIFICATION;
import static ee.sk.smartid.FlowType.QR;
import static ee.sk.smartid.FlowType.WEB2APP;
import static ee.tuleva.onboarding.auth.smartid.SmartIdLoginError.TECHNICAL_ERROR;
import static ee.tuleva.onboarding.auth.smartid.SmartIdLoginError.WRONG_VERIFICATION_CODE;

import ee.sk.smartid.AuthenticationIdentity;
import ee.sk.smartid.DeviceLinkAuthenticationResponseValidator;
import ee.sk.smartid.FlowType;
import ee.sk.smartid.NotificationAuthenticationResponseValidator;
import ee.sk.smartid.exception.UnprocessableSmartIdResponseException;
import ee.sk.smartid.rest.SmartIdConnector;
import ee.sk.smartid.rest.dao.SessionSignature;
import ee.sk.smartid.rest.dao.SessionStatus;
import ee.tuleva.onboarding.auth.SmartIdProperties;
import ee.tuleva.onboarding.auth.response.AuthNotCompleteException;
import jakarta.ws.rs.ServerErrorException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmartIdAuthService {

  private final SmartIdConnector smartIdConnector;
  private final DeviceLinkAuthenticationResponseValidator deviceLinkResponseValidator;
  private final NotificationAuthenticationResponseValidator notificationResponseValidator;
  private final SmartIdCertificateRevocationCheck certificateRevocationCheck;
  private final SmartIdProperties properties;
  private final Clock clock;

  public SmartIdPerson completeLogin(SmartIdSession session) {
    SmartIdPerson person = session.getPerson();
    if (person != null) {
      return person;
    }
    SmartIdLoginError recordedError = session.getError();
    if (recordedError != null) {
      throw new SmartIdException(recordedError);
    }
    try {
      SessionStatus status = finalStatus(session);
      FlowType flowType = flowTypeOfAProducedSignature(status);
      AuthenticationIdentity identity = validate(session, status, flowType);
      requireEstonianAccount(identity);
      certificateRevocationCheck.requireNotRevoked(identity.getAuthCertificate());
      SmartIdPerson authenticated =
          new SmartIdPerson(
              identity, status.getResult().getDocumentNumber(), completedFlow(flowType));
      session.setPerson(authenticated);
      log.info("Smart-ID login completed: sessionId={}", session.getSessionId());
      return authenticated;
    } catch (AuthNotCompleteException e) {
      throw e;
    } catch (Exception e) {
      SmartIdLoginError error = SmartIdLoginError.of(e);
      logFailure(session, error, e);
      session.setError(error);
      throw new SmartIdException(error);
    }
  }

  private static void logFailure(SmartIdSession session, SmartIdLoginError error, Exception e) {
    if (e instanceof SmartIdSessionExpiredException expired) {
      log.info(
          "Smart-ID answered a server error for a session past its lifetime: sessionId={},"
              + " status={}",
          session.getSessionId(),
          expired.status());
    } else if (error == TECHNICAL_ERROR) {
      log.error("Smart-ID login failed: sessionId={}", session.getSessionId(), e);
    } else if (error == WRONG_VERIFICATION_CODE) {
      log.warn(
          "Smart-ID login refused with a wrong verification code, someone other than the"
              + " person may have started it: sessionId={}",
          session.getSessionId());
    } else {
      log.info(
          "Smart-ID login failed: sessionId={}, error={}, reason={}",
          session.getSessionId(),
          error,
          e.getClass().getSimpleName());
    }
  }

  private SessionStatus finalStatus(SmartIdSession session) {
    SessionStatus cached = session.getFinalStatus();
    if (cached != null) {
      return cached;
    }
    SessionStatus status = currentStatus(session);
    if (status == null || !"COMPLETE".equalsIgnoreCase(status.getState())) {
      throw new AuthNotCompleteException();
    }
    session.setFinalStatus(status);
    return status;
  }

  private @Nullable SessionStatus currentStatus(SmartIdSession session) {
    try {
      return smartIdConnector.getSessionStatus(session.getSessionId());
    } catch (ServerErrorException e) {
      if (isPastSmartIdsLifetime(session)) {
        throw new SmartIdSessionExpiredException(e);
      }
      throw e;
    }
  }

  private boolean isPastSmartIdsLifetime(SmartIdSession session) {
    final Duration SAFELY_BELOW_SMART_IDS_SESSION_LIFETIME_OBSERVED_FROM_99_SECONDS =
        Duration.ofSeconds(90);
    return session.hasLived(
        SAFELY_BELOW_SMART_IDS_SESSION_LIFETIME_OBSERVED_FROM_99_SECONDS, Instant.now(clock));
  }

  private AuthenticationIdentity validate(
      SmartIdSession session, SessionStatus status, @Nullable FlowType flowType) {
    return switch (session.getLogin()) {
      case DeviceLinkLogin login -> {
        requireOffered(flowType, Set.of(QR, WEB2APP));
        if (flowType == WEB2APP && session.getUserChallengeVerifier() == null) {
          throw new AuthNotCompleteException();
        }
        yield deviceLinkResponseValidator.validate(
            status, login.request(), session.getUserChallengeVerifier(), properties.schemeName());
      }
      case NotificationLogin login -> {
        requireOffered(flowType, Set.of(NOTIFICATION));
        yield notificationResponseValidator.validate(
            status, login.request(), properties.schemeName());
      }
    };
  }

  private static void requireEstonianAccount(AuthenticationIdentity identity) {
    if (!"EE".equals(identity.getCountry())) {
      throw new UnsupportedSmartIdCountryException(identity.getCountry());
    }
  }

  private static void requireOffered(@Nullable FlowType flowType, Set<FlowType> offered) {
    if (flowType != null && !offered.contains(flowType)) {
      throw new UnprocessableSmartIdResponseException(
          "Unexpected Smart-ID flow type: flowType=" + flowType);
    }
  }

  private static SmartIdCompletedFlow completedFlow(@Nullable FlowType flowType) {
    return switch (flowType) {
      case QR -> SmartIdCompletedFlow.QR_CODE;
      case WEB2APP -> SmartIdCompletedFlow.SAME_DEVICE;
      case NOTIFICATION -> SmartIdCompletedFlow.NOTIFICATION;
      case null, default ->
          throw new UnprocessableSmartIdResponseException(
              "Smart-ID login completed without a usable flow type: flowType=" + flowType);
    };
  }

  private static @Nullable FlowType flowTypeOfAProducedSignature(SessionStatus status) {
    SessionSignature signature = status.getSignature();
    if (signature == null) {
      return null;
    }
    String flowType = signature.getFlowType();
    if (flowType == null || !FlowType.isSupported(flowType)) {
      throw new UnprocessableSmartIdResponseException(
          "Unusable Smart-ID flow type: flowType=" + flowType);
    }
    return FlowType.fromString(flowType);
  }
}
