package ee.tuleva.onboarding.auth.smartid;

import static ee.sk.smartid.AuthenticationCertificateLevel.QUALIFIED;

import ee.sk.smartid.RpChallenge;
import ee.sk.smartid.RpChallengeGenerator;
import ee.sk.smartid.SmartIdClient;
import ee.sk.smartid.VerificationCodeCalculator;
import ee.sk.smartid.common.devicelink.CallbackUrl;
import ee.sk.smartid.common.devicelink.interactions.DeviceLinkInteraction;
import ee.sk.smartid.common.notification.interactions.NotificationInteraction;
import ee.sk.smartid.util.CallbackUrlUtil;
import ee.tuleva.onboarding.auth.SmartIdProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmartIdLoginStarter {

  private static final String LOGIN_PROMPT = "Log in to Tuleva?";

  private final SmartIdClient smartIdClient;
  private final SmartIdProperties properties;
  private final Clock clock;

  public SmartIdSession startDeviceLinkLogin(@Nullable String language) {
    String deviceLinkLanguage = DeviceLinkLanguage.of(language);
    CallbackUrl callbackUrl = CallbackUrlUtil.createCallbackUrl(properties.callbackUrl());
    var builder =
        smartIdClient
            .createDeviceLinkAuthentication()
            .withRpChallenge(RpChallengeGenerator.generate().toBase64EncodedValue())
            .withCertificateLevel(QUALIFIED)
            .withInteractions(List.of(DeviceLinkInteraction.displayTextAndPin(LOGIN_PROMPT)))
            .withInitialCallbackUrl(callbackUrl.initialCallbackUri().toString());
    var response = builder.initAuthenticationSession();
    log.info("Started Smart-ID device link login: sessionId={}", response.sessionID());
    return new SmartIdSession(
        Instant.now(clock),
        new DeviceLinkLogin(
            response.sessionID(),
            response.sessionToken(),
            response.sessionSecret(),
            response.deviceLinkBase(),
            builder.getAuthenticationSessionRequest(),
            callbackUrl.urlToken(),
            callbackUrl.initialCallbackUri().toString(),
            deviceLinkLanguage));
  }

  public SmartIdSession startNotificationLogin(RememberedSmartIdAccount account) {
    RpChallenge rpChallenge = RpChallengeGenerator.generate();
    var builder =
        smartIdClient
            .createNotificationAuthentication()
            .withDocumentNumber(account.documentNumber())
            .withRpChallenge(rpChallenge.toBase64EncodedValue())
            .withCertificateLevel(QUALIFIED)
            .withInteractions(
                List.of(
                    NotificationInteraction.confirmationMessageAndVerificationCodeChoice(
                        LOGIN_PROMPT),
                    NotificationInteraction.displayTextAndPin(LOGIN_PROMPT)));
    var response = builder.initAuthenticationSession();
    log.info("Started Smart-ID notification login: sessionId={}", response.sessionID());
    return new SmartIdSession(
        Instant.now(clock),
        new NotificationLogin(
            response.sessionID(),
            builder.getAuthenticationSessionRequest(),
            VerificationCodeCalculator.calculate(rpChallenge.value())));
  }
}
