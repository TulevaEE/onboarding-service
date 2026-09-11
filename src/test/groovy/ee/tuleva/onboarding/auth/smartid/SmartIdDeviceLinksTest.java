package ee.tuleva.onboarding.auth.smartid;

import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.aDeviceLinkSession;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.aNotificationSession;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.aSessionToken;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.demoProperties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import ee.sk.smartid.SmartIdClient;
import ee.sk.smartid.rest.SmartIdConnector;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SmartIdDeviceLinksTest {

  private final Instant now = Instant.parse("2026-09-02T10:00:00Z");
  private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
  private final SmartIdConnector connector = mock(SmartIdConnector.class);
  private final SmartIdDeviceLinks service =
      new SmartIdDeviceLinks(smartIdClient(), demoProperties, clock);

  private SmartIdClient smartIdClient() {
    var client = new SmartIdClient();
    client.setSmartIdConnector(connector);
    client.setRelyingPartyUUID(demoProperties.relyingPartyUUID());
    client.setRelyingPartyName(demoProperties.relyingPartyName());
    return client;
  }

  @Test
  void qrCodeLinkRotatesWithTheSecondsElapsedSinceTheSessionStarted() {
    SmartIdSession session = aDeviceLinkSession(now.minusSeconds(7));

    URI link = service.qrCodeLink(session);

    assertThat(link.toString())
        .startsWith("https://smart-id.com/device-link/?")
        .contains("deviceLinkType=QR")
        .contains("elapsedSeconds=7")
        .contains("sessionToken=" + aSessionToken)
        .contains("sessionType=auth")
        .contains("lang=est")
        .contains("authCode=");
  }

  @Test
  void web2AppLinkIsStaticAndCarriesNoElapsedSeconds() {
    SmartIdSession session = aDeviceLinkSession(now.minusSeconds(7));

    URI link = service.web2AppLink(session);

    assertThat(link.toString())
        .contains("deviceLinkType=Web2App")
        .doesNotContain("elapsedSeconds")
        .contains("sessionToken=" + aSessionToken)
        .contains("authCode=");
  }

  @Test
  void deviceLinksAreOnlyAvailableForDeviceLinkLogins() {
    SmartIdSession session = aNotificationSession(now);

    assertThatThrownBy(() -> service.qrCodeLink(session))
        .isInstanceOf(SmartIdSessionNotFoundException.class);
  }
}
