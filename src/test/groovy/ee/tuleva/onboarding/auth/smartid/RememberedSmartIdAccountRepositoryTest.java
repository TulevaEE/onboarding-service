package ee.tuleva.onboarding.auth.smartid;

import static ee.tuleva.onboarding.auth.browser.ConcurrentCalls.runTogether;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.documentNumber;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.firstName;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.lastName;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.personalCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.annotation.Transactional;

@JdbcTest
@Import({
  RememberedSmartIdAccountRepository.class,
  RememberedSmartIdAccountRepositoryTest.FixedClockConfig.class
})
@Transactional
class RememberedSmartIdAccountRepositoryTest {

  private static final Instant NOW = Instant.parse("2026-09-03T10:00:00Z");
  private static final Instant LATER = NOW.plus(Duration.ofDays(80));

  @TestConfiguration
  static class FixedClockConfig {
    @Bean
    Clock clock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }

  @Autowired private RememberedSmartIdAccountRepository accounts;
  @Autowired private JdbcClient jdbcClient;

  private static VerifiedSmartIdAccount anAccount() {
    return new VerifiedSmartIdAccount(
        personalCode, documentNumber, firstName, lastName, NOW.minus(Duration.ofDays(10)));
  }

  private static VerifiedSmartIdAccount somebodyElse() {
    return new VerifiedSmartIdAccount(
        "38501010005", "PNOEE-38501010005-MOCK-Q", "Malle", "Mänd", NOW);
  }

  @Test
  void findsTheAccountRememberedOnABrowser() {
    long browser = aBrowser("browser");

    accounts.replace(browser, anAccount(), LATER);

    assertThat(accounts.findUnexpired(browser)).contains(anAccount());
  }

  @Test
  void aBrowserRemembersOneSmartIdAccountTheLatestLoginLeft() {
    long browser = aBrowser("browser");
    accounts.replace(browser, anAccount(), LATER);

    accounts.replace(browser, somebodyElse(), LATER);

    assertThat(accounts.findUnexpired(browser)).contains(somebodyElse());
  }

  @Test
  void doesNotFindAnAccountWhoseValidityHasRunOut() {
    long browser = aBrowser("browser");
    accounts.replace(browser, anAccount(), NOW.minusSeconds(1));

    assertThat(accounts.findUnexpired(browser)).isEmpty();
  }

  @Test
  void forgetsTheAccountOnOneBrowser() {
    long first = aBrowser("first");
    long second = aBrowser("second");
    accounts.replace(first, anAccount(), LATER);
    accounts.replace(second, anAccount(), LATER);

    accounts.remove(first);

    assertThat(accounts.findUnexpired(first)).isEmpty();
    assertThat(accounts.findUnexpired(second)).isPresent();
  }

  @Test
  void forgetsThePersonsAccountOnOneBrowserOnlyWhenThatBrowserRemembersThatPerson() {
    long mine = aBrowser("mine");
    long theirs = aBrowser("theirs");
    long alsoMine = aBrowser("also-mine");
    accounts.replace(mine, anAccount(), LATER);
    accounts.replace(theirs, somebodyElse(), LATER);
    accounts.replace(alsoMine, anAccount(), LATER);

    accounts.remove(mine, personalCode);
    accounts.remove(theirs, personalCode);

    assertThat(accounts.findUnexpired(mine)).isEmpty();
    assertThat(accounts.findUnexpired(theirs)).contains(somebodyElse());
    assertThat(accounts.findUnexpired(alsoMine)).contains(anAccount());
  }

  @Test
  void forgetsOnePersonOnEveryBrowserAndLeavesOthersAlone() {
    long mineOne = aBrowser("mine-one");
    long mineTwo = aBrowser("mine-two");
    long theirs = aBrowser("theirs");
    accounts.replace(mineOne, anAccount(), LATER);
    accounts.replace(mineTwo, anAccount(), LATER);
    accounts.replace(theirs, somebodyElse(), LATER);

    assertThat(accounts.removeAllOf(personalCode)).isEqualTo(2);

    assertThat(accounts.findUnexpired(mineOne)).isEmpty();
    assertThat(accounts.findUnexpired(mineTwo)).isEmpty();
    assertThat(accounts.findUnexpired(theirs)).isPresent();
  }

  @Test
  void purgesOnlyAccountsPastTheirValidity() {
    long expired = aBrowser("expired");
    long valid = aBrowser("valid");
    accounts.replace(expired, anAccount(), NOW.minusSeconds(1));
    accounts.replace(valid, anAccount(), LATER);

    assertThat(accounts.removeExpired()).isEqualTo(1);

    assertThat(accounts.findUnexpired(valid)).isPresent();
  }

  @Test
  void goesWithTheBrowserWhenTheBrowserIsForgotten() {
    long browser = aBrowser("browser");
    accounts.replace(browser, anAccount(), LATER);

    jdbcClient.sql("DELETE FROM remembered_browser WHERE id = :id").param("id", browser).update();

    assertThat(accounts.findUnexpired(browser)).isEmpty();
  }

  @Test
  @Transactional(propagation = NOT_SUPPORTED)
  void concurrentLoginsFromOneBrowserBothSucceedAndLeaveOneRememberedAccount() throws Exception {
    long browser = aBrowser("concurrent-smart-id-browser");
    try {
      runTogether(
          50,
          () -> accounts.replace(browser, anAccount(), LATER),
          () -> accounts.replace(browser, somebodyElse(), LATER));

      assertThat(
              jdbcClient
                  .sql("SELECT count(*) FROM remembered_smart_id_account WHERE browser_id = :id")
                  .param("id", browser)
                  .query(Long.class)
                  .single())
          .isEqualTo(1L);
    } finally {
      jdbcClient.sql("DELETE FROM remembered_browser WHERE id = :id").param("id", browser).update();
    }
  }

  private long aBrowser(String tokenHash) {
    var keyHolder = new GeneratedKeyHolder();
    jdbcClient
        .sql("INSERT INTO remembered_browser (token_hash, expires_at) VALUES (:hash, :expiresAt)")
        .param("hash", tokenHash)
        .param("expiresAt", Timestamp.from(LATER.plus(Duration.ofDays(300))))
        .update(keyHolder, "id");
    return Objects.requireNonNull(keyHolder.getKeyAs(Long.class));
  }
}
