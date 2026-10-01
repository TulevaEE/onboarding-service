package ee.tuleva.onboarding.auth.mobileid;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.auth.browser.ThisBrowser;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RememberedMobileIdPhonesTest {

  private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");
  private static final Duration TWELVE_MONTHS = Duration.ofDays(365);
  private static final long BROWSER_ID = 7L;

  private final ThisBrowser thisBrowser = mock(ThisBrowser.class);
  private final RememberedMobileIdPhoneRepository repository =
      mock(RememberedMobileIdPhoneRepository.class);
  private final RememberedMobileIdPhones phones =
      new RememberedMobileIdPhones(
          thisBrowser, repository, TWELVE_MONTHS, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void aSuccessfulLoginRemembersThePersonsPhoneOnThisBrowserForTwelveMonthsFromNow() {
    given(thisBrowser.rememberUntil(NOW.plus(TWELVE_MONTHS))).willReturn(BROWSER_ID);

    phones.remember("38888888888", "+37255555555");

    verify(repository).save(BROWSER_ID, "38888888888", "+37255555555", NOW.plus(TWELVE_MONTHS));
  }
}
