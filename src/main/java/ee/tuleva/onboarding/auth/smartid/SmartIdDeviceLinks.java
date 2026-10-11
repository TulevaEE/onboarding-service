package ee.tuleva.onboarding.auth.smartid;

import static ee.sk.smartid.DeviceLinkType.QR_CODE;
import static ee.sk.smartid.DeviceLinkType.WEB_2_APP;
import static ee.sk.smartid.SessionType.AUTHENTICATION;

import ee.sk.smartid.DeviceLinkBuilder;
import ee.sk.smartid.DeviceLinkType;
import ee.sk.smartid.SmartIdClient;
import ee.tuleva.onboarding.auth.SmartIdProperties;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SmartIdDeviceLinks {

  private final SmartIdClient smartIdClient;
  private final SmartIdProperties properties;
  private final Clock clock;

  public URI qrCodeLink(SmartIdSession session) {
    DeviceLinkLogin login = deviceLinkLogin(session);
    long elapsedSeconds = Duration.between(session.getCreatedAt(), Instant.now(clock)).getSeconds();
    return deviceLink(login, QR_CODE)
        .withElapsedSeconds(elapsedSeconds)
        .buildDeviceLink(login.sessionSecret());
  }

  public URI web2AppLink(SmartIdSession session) {
    DeviceLinkLogin login = deviceLinkLogin(session);
    return deviceLink(login, WEB_2_APP)
        .withInitialCallbackUrl(login.initialCallbackUrl())
        .buildDeviceLink(login.sessionSecret());
  }

  private DeviceLinkBuilder deviceLink(DeviceLinkLogin login, DeviceLinkType type) {
    return smartIdClient
        .createDynamicContent()
        .withSchemeName(properties.schemeName())
        .withDeviceLinkBase(login.deviceLinkBase().toString())
        .withDeviceLinkType(type)
        .withSessionType(AUTHENTICATION)
        .withSessionToken(login.sessionToken())
        .withLang(login.language())
        .withDigest(login.request().signatureProtocolParameters().rpChallenge())
        .withInteractions(login.request().interactions());
  }

  private static DeviceLinkLogin deviceLinkLogin(SmartIdSession session) {
    if (session.getLogin() instanceof DeviceLinkLogin login) {
      return login;
    }
    throw new SmartIdSessionNotFoundException("Smart-ID device link session was not found.");
  }
}
