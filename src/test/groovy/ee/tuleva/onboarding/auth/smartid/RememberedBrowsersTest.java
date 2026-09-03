package ee.tuleva.onboarding.auth.smartid;

import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.documentNumber;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.firstName;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.lastName;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.personalCode;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@JdbcTest
@Import({RememberedBrowsers.class, RememberedBrowsersTest.FixedClockConfig.class})
@Transactional
class RememberedBrowsersTest {

  private static final Instant NOW = Instant.parse("2026-09-03T10:00:00Z");

  @TestConfiguration
  static class FixedClockConfig {
    @Bean
    Clock clock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }

  @Autowired private RememberedBrowsers browsers;

  private RememberedBrowser aBrowser() {
    return new RememberedBrowser(
        personalCode, documentNumber, firstName, lastName, NOW.minus(Duration.ofDays(10)));
  }

  @Test
  void findsABrowserItRemembered() {
    browsers.add("token-hash", aBrowser(), NOW.plus(Duration.ofDays(80)));

    assertThat(browsers.findUnexpired("token-hash")).contains(aBrowser());
  }

  @Test
  void doesNotFindAnUnknownToken() {
    assertThat(browsers.findUnexpired("never-seen")).isEmpty();
  }

  @Test
  void doesNotFindABrowserWhoseValidityHasRunOut() {
    browsers.add("token-hash", aBrowser(), NOW.minusSeconds(1));

    assertThat(browsers.findUnexpired("token-hash")).isEmpty();
  }

  @Test
  void forgetsASingleBrowser() {
    browsers.add("first", aBrowser(), NOW.plus(Duration.ofDays(80)));
    browsers.add("second", aBrowser(), NOW.plus(Duration.ofDays(80)));

    browsers.remove("first");

    assertThat(browsers.findUnexpired("first")).isEmpty();
    assertThat(browsers.findUnexpired("second")).isPresent();
  }

  @Test
  void forgetsEveryBrowserOfOnePersonAndLeavesOthersAlone() {
    browsers.add("mine-one", aBrowser(), NOW.plus(Duration.ofDays(80)));
    browsers.add("mine-two", aBrowser(), NOW.plus(Duration.ofDays(80)));
    browsers.add(
        "somebody-else",
        new RememberedBrowser("38501010005", "PNOEE-38501010005-MOCK-Q", "Malle", "Mänd", NOW),
        NOW.plus(Duration.ofDays(80)));

    assertThat(browsers.removeAllOf(personalCode)).isEqualTo(2);

    assertThat(browsers.findUnexpired("mine-one")).isEmpty();
    assertThat(browsers.findUnexpired("mine-two")).isEmpty();
    assertThat(browsers.findUnexpired("somebody-else")).isPresent();
  }

  @Test
  void purgesOnlyBrowsersPastTheirValidity() {
    browsers.add("expired", aBrowser(), NOW.minusSeconds(1));
    browsers.add("still-valid", aBrowser(), NOW.plus(Duration.ofDays(80)));

    assertThat(browsers.removeExpired()).isEqualTo(1);

    assertThat(browsers.findUnexpired("still-valid")).isPresent();
  }
}
