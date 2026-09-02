package ee.tuleva.onboarding.auth.smartid;

import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.aDeviceLinkSession;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.aNotificationSession;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.aSessionId;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.anAuthenticationIdentity;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.completeStatus;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.demoProperties;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.documentNumber;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.failedStatus;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.runningStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ee.sk.smartid.DeviceLinkAuthenticationResponseValidator;
import ee.sk.smartid.NotificationAuthenticationResponseValidator;
import ee.sk.smartid.exception.SessionNotFoundException;
import ee.sk.smartid.exception.UnprocessableSmartIdResponseException;
import ee.sk.smartid.exception.useraccount.UserAccountNotFoundException;
import ee.sk.smartid.exception.useraction.UserRefusedException;
import ee.sk.smartid.rest.SmartIdConnector;
import ee.tuleva.onboarding.auth.response.AuthNotCompleteException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SmartIdAuthServiceTest {

  private final Instant now = Instant.parse("2026-09-02T10:00:00Z");
  private final SmartIdConnector connector = mock(SmartIdConnector.class);
  private final DeviceLinkAuthenticationResponseValidator deviceLinkValidator =
      mock(DeviceLinkAuthenticationResponseValidator.class);
  private final NotificationAuthenticationResponseValidator notificationValidator =
      mock(NotificationAuthenticationResponseValidator.class);
  private final SmartIdAuthService service =
      new SmartIdAuthService(connector, deviceLinkValidator, notificationValidator, demoProperties);

  @Test
  void completeLoginKeepsWaitingWhileTheSessionIsRunning() {
    SmartIdSession session = aDeviceLinkSession(now);
    given(connector.getSessionStatus(aSessionId)).willReturn(runningStatus());

    assertThatThrownBy(() -> service.completeLogin(session))
        .isInstanceOf(AuthNotCompleteException.class);
    assertThat(session.getFinalStatus()).isNull();
  }

  @Test
  void completeLoginValidatesAQrLoginAndRecordsThePerson() {
    SmartIdSession session = aDeviceLinkSession(now);
    var status = completeStatus("QR");
    var login = (DeviceLinkLogin) session.getLogin();
    given(connector.getSessionStatus(aSessionId)).willReturn(status);
    given(deviceLinkValidator.validate(status, login.request(), null, "smart-id-demo"))
        .willReturn(anAuthenticationIdentity());

    SmartIdPerson person = service.completeLogin(session);

    assertThat(person).isEqualTo(new SmartIdPerson(anAuthenticationIdentity(), documentNumber));
    assertThat(session.getPerson()).isEqualTo(person);
    assertThat(session.getError()).isNull();
  }

  @Test
  void completeLoginReturnsTheRecordedPersonWithoutAskingSmartIdAgain() {
    SmartIdSession session = aDeviceLinkSession(now);
    session.setPerson(SmartIdFixture.aSmartIdPerson());

    SmartIdPerson person = service.completeLogin(session);

    assertThat(person).isEqualTo(SmartIdFixture.aSmartIdPerson());
    verify(connector, never()).getSessionStatus(any());
  }

  @Test
  void completeLoginWaitsForTheCallbackOnASameDeviceLoginAndCachesTheStatus() {
    SmartIdSession session = aDeviceLinkSession(now);
    given(connector.getSessionStatus(aSessionId)).willReturn(completeStatus("Web2App"));

    assertThatThrownBy(() -> service.completeLogin(session))
        .isInstanceOf(AuthNotCompleteException.class);
    assertThatThrownBy(() -> service.completeLogin(session))
        .isInstanceOf(AuthNotCompleteException.class);

    assertThat(session.getFinalStatus()).isNotNull();
    verify(connector, times(1)).getSessionStatus(aSessionId);
    verify(deviceLinkValidator, never()).validate(any(), any(), any(), any());
  }

  @Test
  void completeLoginValidatesASameDeviceLoginWithTheCallbackVerifier() {
    SmartIdSession session = aDeviceLinkSession(now);
    session.setUserChallengeVerifier("user-challenge-verifier");
    var status = completeStatus("Web2App");
    var login = (DeviceLinkLogin) session.getLogin();
    given(connector.getSessionStatus(aSessionId)).willReturn(status);
    given(
            deviceLinkValidator.validate(
                status, login.request(), "user-challenge-verifier", "smart-id-demo"))
        .willReturn(anAuthenticationIdentity());

    SmartIdPerson person = service.completeLogin(session);

    assertThat(person.getDocumentNumber()).isEqualTo(documentNumber);
  }

  @Test
  void completeLoginRejectsAFlowTypeThatWasNotOffered() {
    SmartIdSession session = aDeviceLinkSession(now);
    given(connector.getSessionStatus(aSessionId)).willReturn(completeStatus("Notification"));

    assertThatThrownBy(() -> service.completeLogin(session))
        .isInstanceOf(SmartIdException.class)
        .extracting(
            e -> ((SmartIdException) e).getErrorsResponse().getErrors().getFirst().getCode())
        .isEqualTo("smart.id.validation.failed");
    assertThat(session.getError()).isEqualTo(SmartIdLoginError.VALIDATION_FAILED);
    verify(deviceLinkValidator, never()).validate(any(), any(), any(), any());
  }

  @Test
  void completeLoginValidatesANotificationLogin() {
    SmartIdSession session = aNotificationSession(now);
    var status = completeStatus("Notification");
    var login = (NotificationLogin) session.getLogin();
    given(connector.getSessionStatus(aSessionId)).willReturn(status);
    given(notificationValidator.validate(status, login.request(), "smart-id-demo"))
        .willReturn(anAuthenticationIdentity());

    SmartIdPerson person = service.completeLogin(session);

    assertThat(person).isEqualTo(new SmartIdPerson(anAuthenticationIdentity(), documentNumber));
    verify(deviceLinkValidator, never()).validate(any(), any(), any(), any());
  }

  @Test
  void completeLoginRecordsARefusalAndKeepsReportingIt() {
    SmartIdSession session = aDeviceLinkSession(now);
    var status = failedStatus("USER_REFUSED");
    given(connector.getSessionStatus(aSessionId)).willReturn(status);
    given(deviceLinkValidator.validate(eq(status), any(), isNull(), eq("smart-id-demo")))
        .willThrow(new UserRefusedException());

    assertThatThrownBy(() -> service.completeLogin(session)).isInstanceOf(SmartIdException.class);
    assertThatThrownBy(() -> service.completeLogin(session))
        .isInstanceOf(SmartIdException.class)
        .extracting(
            e -> ((SmartIdException) e).getErrorsResponse().getErrors().getFirst().getCode())
        .isEqualTo("smart.id.user.refused");

    assertThat(session.getError()).isEqualTo(SmartIdLoginError.USER_REFUSED);
    verify(connector, times(1)).getSessionStatus(aSessionId);
  }

  @Test
  void completeLoginReportsAnExpiredSmartIdSessionAsATimeout() {
    SmartIdSession session = aDeviceLinkSession(now);
    given(connector.getSessionStatus(aSessionId)).willThrow(new SessionNotFoundException());

    assertThatThrownBy(() -> service.completeLogin(session)).isInstanceOf(SmartIdException.class);

    assertThat(session.getError()).isEqualTo(SmartIdLoginError.TIMEOUT);
  }

  @Test
  void completeLoginReportsAMissingAccount() {
    SmartIdSession session = aNotificationSession(now);
    var status = failedStatus("DOCUMENT_UNUSABLE");
    given(connector.getSessionStatus(aSessionId)).willReturn(status);
    given(notificationValidator.validate(eq(status), any(), eq("smart-id-demo")))
        .willThrow(new UserAccountNotFoundException());

    assertThatThrownBy(() -> service.completeLogin(session)).isInstanceOf(SmartIdException.class);

    assertThat(session.getError()).isEqualTo(SmartIdLoginError.ACCOUNT_NOT_FOUND);
  }

  @Test
  void completeLoginReportsAnInvalidResponseAsAValidationFailure() {
    SmartIdSession session = aDeviceLinkSession(now);
    var status = completeStatus("QR");
    given(connector.getSessionStatus(aSessionId)).willReturn(status);
    given(deviceLinkValidator.validate(eq(status), any(), isNull(), eq("smart-id-demo")))
        .willThrow(new UnprocessableSmartIdResponseException("bad signature"));

    assertThatThrownBy(() -> service.completeLogin(session)).isInstanceOf(SmartIdException.class);

    assertThat(session.getError()).isEqualTo(SmartIdLoginError.VALIDATION_FAILED);
  }

  @Test
  void completeLoginReportsUnexpectedFailuresAsTechnicalErrors() {
    SmartIdSession session = aDeviceLinkSession(now);
    given(connector.getSessionStatus(aSessionId)).willThrow(new RuntimeException("boom"));

    assertThatThrownBy(() -> service.completeLogin(session)).isInstanceOf(SmartIdException.class);

    assertThat(session.getError()).isEqualTo(SmartIdLoginError.TECHNICAL_ERROR);
  }
}
