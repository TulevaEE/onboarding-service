package ee.tuleva.onboarding.investment.report.publishing;

import static ee.tuleva.onboarding.investment.report.publishing.InvestmentReportPublishingLock.ONE_PUBLISH_AT_A_TIME_LOCK;
import static java.time.temporal.ChronoUnit.MILLIS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class InvestmentReportPublishingLockIT {

  private static final Duration A_STOPPED_PUBLISH_HOLDS_THE_LOCK_AT_MOST = Duration.ofMinutes(2);
  private static final TimeZone FAR_FROM_UTC = TimeZone.getTimeZone("Pacific/Kiritimati");

  private TimeZone defaultTimeZone;
  private DataSource database;
  private JdbcTemplateLockProvider lockProviderAsInProduction;

  @BeforeEach
  void setUp() {
    defaultTimeZone = TimeZone.getDefault();
    TimeZone.setDefault(FAR_FROM_UTC);
    database = shedLockDatabase();
    lockProviderAsInProduction =
        new JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(database))
                .usingDbTime()
                .build());
  }

  @AfterEach
  void tearDown() {
    new JdbcTemplate(database).execute("SHUTDOWN");
    TimeZone.setDefault(defaultTimeZone);
  }

  @Test
  void aPublishDoesNotRunWhileAnotherHoldsTheLockAndIsToldWhenAStoppedOneWouldLoseIt() {
    var publishingLock = publishingLock(Clock.systemUTC());
    var before = Instant.now().truncatedTo(MILLIS);
    var secondRan = new AtomicBoolean();

    var secondRefusal =
        publishingLock.whileNoOtherPublishRuns(
            firstStillHeld ->
                publishingLock.whileNoOtherPublishRuns(
                    secondStillHeld -> {
                      secondRan.set(true);
                      return "second published";
                    },
                    refusal -> refusal),
            refusal -> "first refused");

    var after = Instant.now().plusMillis(1);
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
  void theDatabaseClockNotTheAppClockDecidesThatTheLockIsHeld() {
    holdTheLockLikeAPublishThatStopped(A_STOPPED_PUBLISH_HOLDS_THE_LOCK_AT_MOST);
    var appClockAhead = Clock.offset(Clock.systemUTC(), Duration.ofMinutes(10));
    var appClockBehind = Clock.offset(Clock.systemUTC(), Duration.ofMinutes(-10));

    assertThat(publishedOrRefusal(publishingLock(appClockAhead)))
        .startsWith("Another investment report publish holds the lock");
    assertThat(publishedOrRefusal(publishingLock(appClockBehind)))
        .startsWith("Another investment report publish holds the lock");
  }

  @Test
  void aPublishRefusedJustAsTheOtherReleasedTheLockIsToldToTryAgain() {
    publishingLock(Clock.systemUTC()).whileNoOtherPublishRuns(stillHeld -> "published", r -> r);
    var providerThatSawTheLockHeld = mock(ExtensibleLockProvider.class);
    given(providerThatSawTheLockHeld.lock(any())).willReturn(Optional.empty());

    var refusal =
        new InvestmentReportPublishingLock(
                providerThatSawTheLockHeld, JdbcClient.create(database), Clock.systemUTC())
            .whileNoOtherPublishRuns(stillHeld -> "published", refused -> refused);

    assertThat(refusal)
        .startsWith(
            "Another investment report publish released the lock just now, try again:"
                + " heldUntil=");
  }

  @Test
  void aPublishIsStillRefusedWhenUntilWhenTheLockIsHeldCannotBeRead() {
    holdTheLockLikeAPublishThatStopped(A_STOPPED_PUBLISH_HOLDS_THE_LOCK_AT_MOST);
    var databaseWithoutTheLockTable = sessionInUtcAsPostgresStoresLockTimes();
    try {
      var publishingLock =
          new InvestmentReportPublishingLock(
              lockProviderAsInProduction,
              JdbcClient.create(databaseWithoutTheLockTable),
              Clock.systemUTC());

      assertThat(publishedOrRefusal(publishingLock))
          .isEqualTo(
              "Another investment report publish holds the lock, and when it lapses could not be"
                  + " read: error=BadSqlGrammarException");
    } finally {
      new JdbcTemplate(databaseWithoutTheLockTable).execute("SHUTDOWN");
    }
  }

  @Test
  void aFinishedPublishReleasesTheLockForTheNextOne() {
    var publishingLock = publishingLock(Clock.systemUTC());
    publishingLock.whileNoOtherPublishRuns(stillHeld -> "published", refused -> refused);

    assertThat(
            publishingLock.<String>whileNoOtherPublishRuns(
                stillHeld -> "published again", refused -> refused))
        .isEqualTo("published again");
  }

  @Test
  void aPublishThatStoppedHoldsTheLockOnlyUntilItsLeaseRunsOut() {
    var lease = Duration.ofSeconds(1);
    holdTheLockLikeAPublishThatStopped(lease);

    sleep(lease.plusMillis(300));

    assertThat(publishedOrRefusal(publishingLock(Clock.systemUTC()))).isEqualTo("published");
  }

  @Test
  void aRealLockIsExtendedInTheDatabaseWhileThePublishRuns() {
    var keepAliveExecutor = mock(ScheduledExecutorService.class);
    var keepAlive = new AtomicReference<Runnable>();
    willAnswer(
            schedule -> {
              keepAlive.set(schedule.getArgument(0));
              return mock(ScheduledFuture.class);
            })
        .given(keepAliveExecutor)
        .scheduleAtFixedRate(any(), anyLong(), anyLong(), eq(MILLISECONDS));
    var publishingLock =
        new InvestmentReportPublishingLock(
            lockProviderAsInProduction,
            JdbcClient.create(database),
            Clock.systemUTC(),
            () -> keepAliveExecutor);
    var heldUntilBefore = new AtomicReference<Instant>();
    var heldUntilAfter = new AtomicReference<Instant>();

    var stillHeldAfterTheExtension =
        publishingLock.whileNoOtherPublishRuns(
            lockStillHeld -> {
              heldUntilBefore.set(heldUntilSeenByAnotherPublish(publishingLock));
              sleep(Duration.ofMillis(50));
              keepAlive.get().run();
              heldUntilAfter.set(heldUntilSeenByAnotherPublish(publishingLock));
              return lockStillHeld.getAsBoolean();
            },
            refusal -> false);

    assertThat(stillHeldAfterTheExtension).isTrue();
    assertThat(heldUntilAfter.get()).isAfter(heldUntilBefore.get());
  }

  private static Instant heldUntilSeenByAnotherPublish(InvestmentReportPublishingLock lock) {
    return heldUntilIn(publishedOrRefusal(lock));
  }

  private static String publishedOrRefusal(InvestmentReportPublishingLock lock) {
    return lock.whileNoOtherPublishRuns(stillHeld -> "published", refusal -> refusal);
  }

  private InvestmentReportPublishingLock publishingLock(Clock clock) {
    return new InvestmentReportPublishingLock(
        lockProviderAsInProduction, JdbcClient.create(database), clock);
  }

  private void holdTheLockLikeAPublishThatStopped(Duration lease) {
    assertThat(
            lockProviderAsInProduction.lock(
                new LockConfiguration(
                    Instant.now(), ONE_PUBLISH_AT_A_TIME_LOCK, lease, Duration.ZERO)))
        .isPresent();
  }

  private static Instant heldUntilIn(String refusal) {
    return Instant.parse(
        refusal.substring(refusal.lastIndexOf("heldUntil=") + "heldUntil=".length()));
  }

  private static DataSource shedLockDatabase() {
    var dataSource = sessionInUtcAsPostgresStoresLockTimes();
    new ResourceDatabasePopulator(
            new ClassPathResource("db/migration/V1_127__shedlock_scheduling_job_lock.sql"),
            new ClassPathResource("db/migration/V1_128__shedlock_extend_name_field.sql"))
        .execute(dataSource);
    return dataSource;
  }

  private static DataSource sessionInUtcAsPostgresStoresLockTimes() {
    return new DriverManagerDataSource(
        "jdbc:h2:mem:publishing-lock-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;TIME ZONE=UTC");
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
