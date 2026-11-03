package ee.tuleva.onboarding.investment.report.publishing;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static java.time.temporal.ChronoUnit.MILLIS;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
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

  private static final Duration LEASE_FOR_THE_PENSION_FUNDS = Duration.parse("PT43M9S");

  private EmbeddedDatabase database;
  private InvestmentReportPublishingLock publishingLock;

  @BeforeEach
  void setUp() {
    database =
        new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .generateUniqueName(true)
            .addScript("db/migration/V1_127__shedlock_scheduling_job_lock.sql")
            .addScript("db/migration/V1_128__shedlock_extend_name_field.sql")
            .build();
    var lockProviderAsInProduction =
        new JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(database))
                .usingDbTime()
                .build());
    publishingLock =
        new InvestmentReportPublishingLock(
            lockProviderAsInProduction,
            JdbcClient.create(database),
            Clock.systemUTC(),
            new InvestmentReportPublishingProperties(List.of(TUK75, TUK00, TUV100)));
  }

  @AfterEach
  void tearDown() {
    database.shutdown();
  }

  @Test
  void aPublishDoesNotRunWhileAnotherHoldsTheLockAndIsToldWhenItsLeaseRunsOut() {
    var before = Instant.now().truncatedTo(MILLIS);
    var secondRan = new AtomicBoolean();

    Optional<Instant> secondRefusedUntil =
        publishingLock.whileNoOtherPublishRuns(
            () ->
                publishingLock.whileNoOtherPublishRuns(
                    () -> {
                      secondRan.set(true);
                      return Optional.empty();
                    },
                    Optional::of),
            firstRefusedUntil -> Optional.empty());

    var after = Instant.now();
    assertThat(secondRan).isFalse();
    assertThat(secondRefusedUntil)
        .hasValueSatisfying(
            heldUntil ->
                assertThat(heldUntil)
                    .isBetween(
                        before.plus(LEASE_FOR_THE_PENSION_FUNDS),
                        after.plus(LEASE_FOR_THE_PENSION_FUNDS)));
  }

  @Test
  void aFinishedPublishReleasesTheLockForTheNextOne() {
    publishingLock.whileNoOtherPublishRuns(() -> "published", heldUntil -> "refused");

    assertThat(
            publishingLock.whileNoOtherPublishRuns(
                () -> "published again", heldUntil -> "refused until " + heldUntil))
        .isEqualTo("published again");
  }
}
