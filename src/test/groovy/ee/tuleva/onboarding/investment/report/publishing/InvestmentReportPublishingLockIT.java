package ee.tuleva.onboarding.investment.report.publishing;

import static ee.tuleva.onboarding.investment.report.publishing.InvestmentReportPublishingLock.ONE_PUBLISH_AT_A_TIME_LOCK;
import static java.time.temporal.ChronoUnit.MILLIS;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class InvestmentReportPublishingLockIT {

  private static final Duration A_STOPPED_PUBLISH_HOLDS_THE_LOCK_AT_MOST = Duration.ofMinutes(2);
  private static final TimeZone FAR_FROM_UTC = TimeZone.getTimeZone("Pacific/Kiritimati");

  private TimeZone defaultTimeZone;
  private EmbeddedDatabase database;
  private JdbcTemplateLockProvider lockProviderStoringUtcAsPostgresDoes;

  @BeforeEach
  void setUp() {
    defaultTimeZone = TimeZone.getDefault();
    TimeZone.setDefault(FAR_FROM_UTC);
    database = shedLockDatabase();
    lockProviderStoringUtcAsPostgresDoes =
        new JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(database))
                .forceUtcTimeZone()
                .build());
  }

  @AfterEach
  void tearDown() {
    database.shutdown();
    TimeZone.setDefault(defaultTimeZone);
  }

  @Test
  void aPublishDoesNotRunWhileAnotherHoldsTheLockAndIsToldWhenAStoppedOneWouldLoseIt() {
    var publishingLock = publishingLock(Clock.systemUTC());
    var before = Instant.now().truncatedTo(MILLIS);
    var secondRan = new AtomicBoolean();

    var secondRefusal =
        publishingLock.whileNoOtherPublishRuns(
            () ->
                publishingLock.whileNoOtherPublishRuns(
                    () -> {
                      secondRan.set(true);
                      return "second published";
                    },
                    refusal -> refusal),
            refusal -> "first refused");

    var after = Instant.now();
    assertThat(secondRan).isFalse();
    assertThat(secondRefusal)
        .startsWith(
            "Another investment report publish holds the lock and extends it while it runs;"
                + " one that stopped loses it by heldUntil: heldUntil=");
    assertThat(heldUntilIn(secondRefusal))
        .isBetween(
            before.plus(A_STOPPED_PUBLISH_HOLDS_THE_LOCK_AT_MOST),
            after.plus(A_STOPPED_PUBLISH_HOLDS_THE_LOCK_AT_MOST));
  }

  @Test
  void aPublishRefusedJustAsTheOtherReleasedTheLockIsToldToTryAgain() {
    var otherPublishLeaseEnd = holdTheLockLikeAPublishThatStopped(Duration.ofMinutes(2));
    var clockPastTheOtherLease = Clock.offset(Clock.systemUTC(), Duration.ofMinutes(10));

    var refusal =
        publishingLock(clockPastTheOtherLease)
            .whileNoOtherPublishRuns(() -> "published", refused -> refused);

    assertThat(refusal)
        .isEqualTo(
            "Another investment report publish released the lock just now, try again: heldUntil="
                + otherPublishLeaseEnd);
  }

  @Test
  void aPublishIsStillRefusedWhenUntilWhenTheLockIsHeldCannotBeRead() {
    holdTheLockLikeAPublishThatStopped(Duration.ofMinutes(2));
    var databaseWithoutTheLockTable =
        new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .generateUniqueName(true)
            .build();
    try {
      var publishingLock =
          new InvestmentReportPublishingLock(
              lockProviderStoringUtcAsPostgresDoes,
              JdbcClient.create(databaseWithoutTheLockTable),
              Clock.systemUTC());

      assertThat(publishingLock.whileNoOtherPublishRuns(() -> "published", refused -> refused))
          .isEqualTo(
              "Another investment report publish holds the lock, and when it lapses could not be"
                  + " read: error=BadSqlGrammarException");
    } finally {
      databaseWithoutTheLockTable.shutdown();
    }
  }

  @Test
  void aFinishedPublishReleasesTheLockForTheNextOne() {
    var publishingLock = publishingLock(Clock.systemUTC());
    publishingLock.whileNoOtherPublishRuns(() -> "published", refused -> refused);

    assertThat(publishingLock.whileNoOtherPublishRuns(() -> "published again", refused -> refused))
        .isEqualTo("published again");
  }

  @Test
  void aPublishThatStoppedHoldsTheLockOnlyUntilItsLeaseRunsOut() {
    var lease = Duration.ofSeconds(1);
    holdTheLockLikeAPublishThatStopped(lease);

    sleep(lease.plusMillis(300));

    assertThat(
            publishingLock(Clock.systemUTC())
                .whileNoOtherPublishRuns(() -> "published", refused -> refused))
        .isEqualTo("published");
  }

  private InvestmentReportPublishingLock publishingLock(Clock clock) {
    return new InvestmentReportPublishingLock(
        lockProviderStoringUtcAsPostgresDoes, JdbcClient.create(database), clock);
  }

  private Instant holdTheLockLikeAPublishThatStopped(Duration lease) {
    var lockedAt = Instant.now().truncatedTo(MILLIS);
    assertThat(
            lockProviderStoringUtcAsPostgresDoes.lock(
                new LockConfiguration(lockedAt, ONE_PUBLISH_AT_A_TIME_LOCK, lease, Duration.ZERO)))
        .isPresent();
    return lockedAt.plus(lease);
  }

  private static Instant heldUntilIn(String refusal) {
    return Instant.parse(
        refusal.substring(refusal.lastIndexOf("heldUntil=") + "heldUntil=".length()));
  }

  private static EmbeddedDatabase shedLockDatabase() {
    return new EmbeddedDatabaseBuilder()
        .setType(EmbeddedDatabaseType.H2)
        .generateUniqueName(true)
        .addScript("db/migration/V1_127__shedlock_scheduling_job_lock.sql")
        .addScript("db/migration/V1_128__shedlock_extend_name_field.sql")
        .build();
  }

  private static void sleep(Duration duration) {
    try {
      Thread.sleep(duration);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
