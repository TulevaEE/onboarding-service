package ee.tuleva.onboarding.auth.smartid;

import static ee.tuleva.onboarding.auth.browser.PushLogin.SMART_ID_NOTIFICATION;

import ee.tuleva.onboarding.auth.browser.RememberedBrowser;
import ee.tuleva.onboarding.auth.browser.ThisBrowser;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RememberedSmartIdAccounts {

  private final ThisBrowser thisBrowser;
  private final RememberedSmartIdAccountRepository accounts;
  private final Duration validity;
  private final Clock clock;

  public RememberedSmartIdAccounts(
      ThisBrowser thisBrowser,
      RememberedSmartIdAccountRepository accounts,
      @Value("${smartid.remembered-browser-validity:90d}") Duration validity,
      Clock clock) {
    this.thisBrowser = thisBrowser;
    this.accounts = accounts;
    this.validity = validity;
    this.clock = clock;
  }

  public Optional<RememberedSmartIdAccount> current() {
    return currentVerification().map(VerifiedSmartIdAccount::toAccount);
  }

  public void remember(SmartIdPerson person, boolean deviceLinkVerified) {
    Optional<Instant> verifiedAt =
        deviceLinkVerified ? Optional.of(Instant.now(clock)) : earlierDeviceLinkVerification();
    verifiedAt.ifPresent(at -> rememberVerifiedAt(person, at));
  }

  private Optional<Instant> earlierDeviceLinkVerification() {
    return currentVerification().map(VerifiedSmartIdAccount::verifiedAt);
  }

  private void rememberVerifiedAt(SmartIdPerson person, Instant verifiedAt) {
    Instant expiresAt = verifiedAt.plus(validity);
    long browserId = thisBrowser.rememberUntil(expiresAt);
    accounts.replace(
        browserId,
        new VerifiedSmartIdAccount(
            person.getPersonalCode(),
            person.getDocumentNumber(),
            person.getFirstName(),
            person.getLastName(),
            verifiedAt),
        expiresAt);
  }

  public void forget() {
    thisBrowser.remembered().map(RememberedBrowser::id).ifPresent(accounts::remove);
  }

  public void forgetEverywhere() {
    currentVerification()
        .ifPresent(
            account -> {
              int forgotten = accounts.removeAllOf(account.personalCode());
              log.info("Forgot every remembered Smart-ID account: forgotten={}", forgotten);
            });
  }

  public void claimNotificationLoginStart() {
    thisBrowser.claimLoginStart(SMART_ID_NOTIFICATION);
  }

  private Optional<VerifiedSmartIdAccount> currentVerification() {
    return thisBrowser.remembered().map(RememberedBrowser::id).flatMap(accounts::findUnexpired);
  }
}
