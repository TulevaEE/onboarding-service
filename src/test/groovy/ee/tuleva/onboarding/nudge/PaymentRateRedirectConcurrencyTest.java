package ee.tuleva.onboarding.nudge;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@TestPropertySource(
    properties = {
      "nudge.payment-rate-redirect.seed=season-2026-test-seed",
      "nudge.payment-rate-redirect.holdout-percent=20",
      "nudge.payment-rate-redirect.start-date=2026-09-01",
      "spring.datasource.hikari.maximum-pool-size=20"
    })
class PaymentRateRedirectConcurrencyTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final String TREATMENT_PERSONAL_CODE = "38888880000";

  @Autowired private PaymentRateRedirectService paymentRateRedirectService;
  @Autowired private JdbcClient jdbcClient;
  @Autowired private DataSource dataSource;

  @MockitoBean private PaymentRateRedirectEligibility eligibility;
  @MockitoBean private UserService userService;

  private long userId;
  private String personalCode;

  @BeforeEach
  void setUp() {
    ClockHolder.setClock(
        Clock.fixed(
            LocalDateTime.parse("2026-09-15T09:00:00").atZone(TALLINN).toInstant(), TALLINN));
    personalCode = "3888888" + UUID.randomUUID().toString().replaceAll("\\D", "").substring(0, 4);
    userId = insertUser(personalCode);
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
    jdbcClient
        .sql("DELETE FROM nudge_exposure WHERE user_id = :userId")
        .param("userId", userId)
        .update();
    jdbcClient.sql("DELETE FROM users WHERE id = :userId").param("userId", userId).update();
  }

  @Test
  void twoSimultaneousLandingsConsumeTheSeasonOnce() throws Exception {
    assumeTrue(isPostgres(), "The insert-on-conflict race only reproduces on PostgreSQL");

    User user = sampleUser().id(userId).personalCode(personalCode).build();
    given(userService.getByIdOrThrow(userId)).willReturn(user);
    given(eligibility.isEligible(any())).willReturn(true);
    AuthenticatedPerson person =
        AuthenticatedPerson.builder()
            .firstName("Nudge")
            .lastName("Exposure")
            .personalCode(TREATMENT_PERSONAL_CODE)
            .userId(userId)
            .role(null)
            .build();

    int threads = 2;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CyclicBarrier barrier = new CyclicBarrier(threads);
    List<PaymentRateRedirect> answers = new CopyOnWriteArrayList<>();
    List<Throwable> errors = new CopyOnWriteArrayList<>();
    List<Future<?>> futures = new ArrayList<>();

    try {
      for (int i = 0; i < threads; i++) {
        futures.add(
            pool.submit(
                () -> {
                  try {
                    barrier.await();
                    answers.add(paymentRateRedirectService.assign(person));
                  } catch (Throwable t) {
                    errors.add(t);
                  }
                  return null;
                }));
      }
      for (Future<?> future : futures) {
        future.get(60, SECONDS);
      }
    } finally {
      pool.shutdownNow();
    }

    assertThat(errors).isEmpty();
    assertThat(answers)
        .containsExactlyInAnyOrder(
            PaymentRateRedirect.to(ExperimentArm.TREATMENT, 2026), PaymentRateRedirect.no());
    assertThat(exposureCount()).isEqualTo(1);
  }

  private long exposureCount() {
    return jdbcClient
        .sql("SELECT count(*) FROM nudge_exposure WHERE user_id = :userId")
        .param("userId", userId)
        .query(Long.class)
        .single();
  }

  private long insertUser(String code) {
    jdbcClient
        .sql(
            """
            INSERT INTO users (active, personal_code, first_name, last_name, email,
                               created_date, updated_date)
            VALUES (true, :personalCode, 'Nudge', 'Exposure', :email, :now, :now)
            """)
        .param("personalCode", code)
        .param("email", code + "@example.invalid")
        .param("now", Timestamp.from(ClockHolder.clock().instant()))
        .update();
    return jdbcClient
        .sql("SELECT id FROM users WHERE personal_code = :personalCode")
        .param("personalCode", code)
        .query(Long.class)
        .single();
  }

  private boolean isPostgres() throws SQLException {
    try (var connection = dataSource.getConnection()) {
      return connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgresql");
    }
  }
}
