package ee.tuleva.onboarding.auth.mobileid;

import static ee.tuleva.onboarding.auth.browser.PushLogin.MOBILE_ID;

import ee.tuleva.onboarding.auth.browser.ThisBrowser;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RememberedMobileIdPhones {

  private final ThisBrowser thisBrowser;
  private final RememberedMobileIdPhoneRepository phones;
  private final Duration validity;
  private final Clock clock;

  public RememberedMobileIdPhones(
      ThisBrowser thisBrowser,
      RememberedMobileIdPhoneRepository phones,
      @Value("${mobile-id.remembered-phone-validity:365d}") Duration validity,
      Clock clock) {
    this.thisBrowser = thisBrowser;
    this.phones = phones;
    this.validity = validity;
    this.clock = clock;
  }

  public boolean isRemembered(String personalCode) {
    return find(personalCode).isPresent();
  }

  Optional<RememberedMobileIdPhone> find(String personalCode) {
    return thisBrowser
        .remembered()
        .flatMap(browser -> phones.findUnexpired(browser.id(), personalCode));
  }

  void claimLoginStart() {
    thisBrowser.claimLoginStart(MOBILE_ID);
  }

  public void remember(String personalCode, String phoneNumber) {
    Instant expiresAt = Instant.now(clock).plus(validity);
    long browserId = thisBrowser.rememberUntil(expiresAt);
    phones.save(browserId, personalCode, phoneNumber, expiresAt);
  }
}
