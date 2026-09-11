package ee.tuleva.onboarding.auth.smartid;

import static org.springframework.http.HttpStatus.NO_CONTENT;

import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/smart-id/login")
@RequiredArgsConstructor
@Slf4j
public class SmartIdLoginController {

  private final SmartIdLoginStarter smartIdLoginStarter;
  private final SmartIdDeviceLinks smartIdDeviceLinks;
  private final RememberedSmartIdAccounts rememberedSmartIdAccounts;
  private final GenericSessionStore sessionStore;

  @GetMapping("/remembered-account")
  public ResponseEntity<RememberedSmartIdAccountResponse> rememberedAccount() {
    return rememberedSmartIdAccounts
        .current()
        .map(RememberedSmartIdAccountResponse::from)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  @DeleteMapping("/remembered-account")
  @ResponseStatus(NO_CONTENT)
  public void forgetRememberedAccount() {
    rememberedSmartIdAccounts.forget();
  }

  @PostMapping
  public SmartIdLoginResponse start(@Valid @RequestBody StartSmartIdLoginCommand command) {
    return switch (command.flow()) {
      case DEVICE_LINK -> startDeviceLinkLogin(command.language());
      case NOTIFICATION -> startNotificationLogin();
    };
  }

  @GetMapping("/qr-code")
  public SmartIdQrCodeResponse qrCode() {
    return new SmartIdQrCodeResponse(smartIdDeviceLinks.qrCodeLink(currentSession()).toString());
  }

  @PostMapping("/callback")
  @ResponseStatus(NO_CONTENT)
  public void callback(@Valid @RequestBody SmartIdCallback callback) {
    SmartIdSession session = currentSession();
    session.acceptCallback(callback);
    log.info("Accepted Smart-ID callback: sessionId={}", session.getSessionId());
    sessionStore.save(session);
  }

  private SmartIdLoginResponse startDeviceLinkLogin(@Nullable String language) {
    SmartIdSession session = smartIdLoginStarter.startDeviceLinkLogin(language);
    sessionStore.save(session);
    return SmartIdLoginResponse.deviceLink(smartIdDeviceLinks.web2AppLink(session).toString());
  }

  private SmartIdLoginResponse startNotificationLogin() {
    RememberedSmartIdAccount account =
        rememberedSmartIdAccounts
            .current()
            .orElseThrow(
                () -> new SmartIdSessionNotFoundException("No remembered Smart-ID account."));
    SmartIdSession session = startNotificationLogin(account);
    sessionStore.save(session);
    return SmartIdLoginResponse.notification(
        ((NotificationLogin) session.getLogin()).verificationCode());
  }

  private SmartIdSession startNotificationLogin(RememberedSmartIdAccount account) {
    try {
      return smartIdLoginStarter.startNotificationLogin(account);
    } catch (SmartIdException e) {
      if (e.getLoginError() == SmartIdLoginError.ACCOUNT_NOT_FOUND) {
        rememberedSmartIdAccounts.forgetEverywhere();
      }
      throw e;
    }
  }

  private SmartIdSession currentSession() {
    return sessionStore.get(SmartIdSession.class).orElseThrow(SmartIdSessionNotFoundException::new);
  }
}
