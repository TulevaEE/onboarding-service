package ee.tuleva.onboarding.auth.smartid;

import static ee.sk.smartid.FlowType.NOTIFICATION;
import static ee.sk.smartid.FlowType.QR;
import static ee.sk.smartid.FlowType.WEB2APP;
import static ee.tuleva.onboarding.auth.smartid.SmartIdLoginError.TECHNICAL_ERROR;

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
  private final SmartIdProperties properties;

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
      AuthenticationIdentity identity = validate(session, status, flowTypeOf(status));
      requireEstonianAccount(identity);
      SmartIdPerson authenticated =
          new SmartIdPerson(identity, status.getResult().getDocumentNumber());
      session.setPerson(authenticated);
      log.info("Smart-ID login completed: sessionId={}", session.getSessionId());
      return authenticated;
    } catch (AuthNotCompleteException e) {
      throw e;
    } catch (Exception e) {
      SmartIdLoginError error = SmartIdLoginError.of(e);
      if (error == TECHNICAL_ERROR) {
        log.error("Smart-ID login failed: sessionId={}", session.getSessionId(), e);
      } else {
        log.info(
            "Smart-ID login failed: sessionId={}, error={}, reason={}",
            session.getSessionId(),
            error,
            e.getClass().getSimpleName());
      }
      session.setError(error);
      throw new SmartIdException(error);
    }
  }

  private SessionStatus finalStatus(SmartIdSession session) {
    SessionStatus cached = session.getFinalStatus();
    if (cached != null) {
      return cached;
    }
    SessionStatus status = smartIdConnector.getSessionStatus(session.getSessionId());
    if (status == null || !"COMPLETE".equalsIgnoreCase(status.getState())) {
      throw new AuthNotCompleteException();
    }
    session.setFinalStatus(status);
    return status;
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

  private static @Nullable FlowType flowTypeOf(SessionStatus status) {
    SessionSignature signature = status.getSignature();
    if (signature == null || signature.getFlowType() == null) {
      return null;
    }
    if (!FlowType.isSupported(signature.getFlowType())) {
      throw new UnprocessableSmartIdResponseException(
          "Unsupported Smart-ID flow type: flowType=" + signature.getFlowType());
    }
    return FlowType.fromString(signature.getFlowType());
  }
}
