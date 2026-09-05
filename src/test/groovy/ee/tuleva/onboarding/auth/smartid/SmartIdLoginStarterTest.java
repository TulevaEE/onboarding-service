package ee.tuleva.onboarding.auth.smartid;

import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.aDeviceLinkSessionResponse;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.aRememberedAccount;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.aSessionId;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.aSessionSecret;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.aSessionToken;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.demoProperties;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.documentNumber;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import ee.sk.smartid.SmartIdClient;
import ee.sk.smartid.VerificationCodeCalculator;
import ee.sk.smartid.exception.permanent.ServerMaintenanceException;
import ee.sk.smartid.exception.useraccount.UserAccountNotFoundException;
import ee.sk.smartid.rest.SmartIdConnector;
import ee.sk.smartid.rest.dao.NotificationAuthenticationSessionResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class SmartIdLoginStarterTest {

  private final Instant now = Instant.parse("2026-09-02T10:00:00Z");
  private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
  private final SmartIdConnector connector = mock(SmartIdConnector.class);
  private final SmartIdLoginStarter service =
      new SmartIdLoginStarter(smartIdClient(), demoProperties, clock);

  private SmartIdClient smartIdClient() {
    var client = new SmartIdClient();
    client.setSmartIdConnector(connector);
    client.setRelyingPartyUUID(demoProperties.relyingPartyUUID());
    client.setRelyingPartyName(demoProperties.relyingPartyName());
    return client;
  }

  @Test
  void startDeviceLinkLoginStartsAnAnonymousSessionWithACallbackUrl() {
    given(connector.initAnonymousDeviceLinkAuthentication(any()))
        .willReturn(aDeviceLinkSessionResponse(aSessionId));

    SmartIdSession session = service.startDeviceLinkLogin("et");

    assertThat(session.getCreatedAt()).isEqualTo(now);
    assertThat(session.getLogin()).isInstanceOf(DeviceLinkLogin.class);
    var login = (DeviceLinkLogin) session.getLogin();
    assertThat(login.sessionId()).isEqualTo(aSessionId);
    assertThat(login.sessionToken()).isEqualTo(aSessionToken);
    assertThat(login.sessionSecret()).isEqualTo(aSessionSecret);
    assertThat(login.language()).isEqualTo("est");
    assertThat(login.initialCallbackUrl())
        .startsWith(demoProperties.callbackUrl() + "?value=")
        .isEqualTo(login.request().initialCallbackUrl())
        .endsWith(login.callbackToken());
    assertThat(login.request().relyingPartyName()).isEqualTo("DEMO");
    assertThat(login.request().certificateLevel()).isEqualTo("QUALIFIED");
    assertThat(login.request().interactions()).isNotBlank();
  }

  @Test
  void startDeviceLinkLoginDefaultsToEstonianWhenTheLanguageIsMissing() {
    given(connector.initAnonymousDeviceLinkAuthentication(any()))
        .willReturn(aDeviceLinkSessionResponse(aSessionId));

    SmartIdSession session = service.startDeviceLinkLogin(null);

    assertThat(((DeviceLinkLogin) session.getLogin()).language()).isEqualTo("est");
  }

  @Test
  void startDeviceLinkLoginFallsBackToEstonianForAnUnknownLanguage() {
    given(connector.initAnonymousDeviceLinkAuthentication(any()))
        .willReturn(aDeviceLinkSessionResponse(aSessionId));

    SmartIdSession session = service.startDeviceLinkLogin("zz");

    assertThat(((DeviceLinkLogin) session.getLogin()).language()).isEqualTo("est");
  }

  @Test
  void startDeviceLinkLoginTranslatesTheUiLanguageToTheDeviceLinkLanguage() {
    given(connector.initAnonymousDeviceLinkAuthentication(any()))
        .willReturn(aDeviceLinkSessionResponse(aSessionId));

    assertThat(((DeviceLinkLogin) service.startDeviceLinkLogin("en").getLogin()).language())
        .isEqualTo("eng");
    assertThat(((DeviceLinkLogin) service.startDeviceLinkLogin("ru").getLogin()).language())
        .isEqualTo("rus");
  }

  @Test
  void startNotificationLoginReportsAnAccountThatSmartIdNoLongerKnows() {
    given(connector.initNotificationAuthentication(any(), eq(documentNumber)))
        .willThrow(new UserAccountNotFoundException());

    assertThatThrownBy(() -> service.startNotificationLogin(aRememberedAccount()))
        .isInstanceOf(SmartIdException.class)
        .extracting(e -> ((SmartIdException) e).getLoginError())
        .isEqualTo(SmartIdLoginError.ACCOUNT_NOT_FOUND);
  }

  @Test
  void startDeviceLinkLoginReportsAnUnavailableServiceAsATechnicalError() {
    given(connector.initAnonymousDeviceLinkAuthentication(any()))
        .willThrow(new ServerMaintenanceException());

    assertThatThrownBy(() -> service.startDeviceLinkLogin("et"))
        .isInstanceOf(SmartIdException.class)
        .extracting(e -> ((SmartIdException) e).getLoginError())
        .isEqualTo(SmartIdLoginError.TECHNICAL_ERROR);
  }

  @Test
  void startNotificationLoginPushesToTheRememberedAccountAndComputesTheVerificationCode() {
    given(connector.initNotificationAuthentication(any(), eq(documentNumber)))
        .willReturn(new NotificationAuthenticationSessionResponse(aSessionId));

    SmartIdSession session = service.startNotificationLogin(aRememberedAccount());

    assertThat(session.getLogin()).isInstanceOf(NotificationLogin.class);
    var login = (NotificationLogin) session.getLogin();
    assertThat(login.sessionId()).isEqualTo(aSessionId);
    byte[] rpChallenge =
        Base64.getDecoder().decode(login.request().signatureProtocolParameters().rpChallenge());
    assertThat(login.verificationCode())
        .hasSize(4)
        .isEqualTo(VerificationCodeCalculator.calculate(rpChallenge));
    assertThat(login.request().vcType()).isEqualTo("numeric4");
  }
}
