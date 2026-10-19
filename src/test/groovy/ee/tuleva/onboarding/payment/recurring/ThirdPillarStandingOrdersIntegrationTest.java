package ee.tuleva.onboarding.payment.recurring;

import static ee.tuleva.onboarding.analytics.AnalyticsThirdPillarTransactionFixture.CREATE_ANALYTICS_SCHEMA;
import static ee.tuleva.onboarding.analytics.AnalyticsThirdPillarTransactionFixture.CREATE_THIRD_PILLAR_TABLE;
import static ee.tuleva.onboarding.analytics.AnalyticsThirdPillarTransactionFixture.exampleTransactionBuilder;
import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentType.GIFT;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentType.SINGLE;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.analytics.ThirdPillarAnalytics;
import ee.tuleva.onboarding.payment.Payment;
import ee.tuleva.onboarding.payment.PaymentData.PaymentType;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.user.User;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({
  ThirdPillarStandingOrders.class,
  ThirdPillarAnalytics.class,
  ThirdPillarStandingOrdersIntegrationTest.FixedClock.class
})
class ThirdPillarStandingOrdersIntegrationTest {

  private static final String OWN_SOURCE = "Osakute väljalase isikult laekumiste alusel";
  private static final String EMPLOYER_SOURCE = "Osakute väljalase tööandjalt laekumiste alusel";
  private static final BigDecimal MONTHLY_AMOUNT = new BigDecimal("100.00");
  private static final BigDecimal ONE_OFF_AMOUNT = new BigDecimal("50.00");

  @TestConfiguration
  static class FixedClock {
    @Bean
    Clock clock() {
      return Clock.fixed(Instant.parse("2026-10-19T09:00:00Z"), UTC);
    }
  }

  @Autowired private TestEntityManager entityManager;
  @Autowired private ThirdPillarStandingOrders standingOrders;

  private User saver;

  @BeforeAll
  static void createAnalyticsTable(@Autowired DataSource dataSource) throws Exception {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(CREATE_ANALYTICS_SCHEMA);
      statement.execute(CREATE_THIRD_PILLAR_TABLE);
    }
  }

  @BeforeEach
  void persistSaver() {
    saver = entityManager.persist(sampleUserNonMember().id(null).build());
  }

  @AfterEach
  void resetClock() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void detectsAStandingOrderFromOwnContributionsInThreeOfTheLastFourMonths() {
    contribution(OWN_SOURCE, LocalDate.of(2026, 7, 10));
    contribution(OWN_SOURCE, LocalDate.of(2026, 8, 10));
    contribution(OWN_SOURCE, LocalDate.of(2026, 9, 10));

    assertThat(standingOrders.paysByStandingOrder(saver.getPersonalCode())).isTrue();
  }

  @Test
  void doesNotDetectAStandingOrderFromOwnContributionsInTwoOfTheLastFourMonths() {
    contribution(OWN_SOURCE, LocalDate.of(2026, 6, 30));
    contribution(OWN_SOURCE, LocalDate.of(2026, 8, 10));
    contribution(OWN_SOURCE, LocalDate.of(2026, 9, 10));

    assertThat(standingOrders.paysByStandingOrder(saver.getPersonalCode())).isFalse();
  }

  @Test
  void doesNotCountAMonthWhoseContributionWasPaidThroughOurPaymentLink() {
    contribution(OWN_SOURCE, LocalDate.of(2026, 7, 10));
    contribution(OWN_SOURCE, LocalDate.of(2026, 8, 10));
    linkPayment(SINGLE, Instant.parse("2026-09-08T10:00:00Z"));
    contribution(OWN_SOURCE, LocalDate.of(2026, 9, 9));

    assertThat(standingOrders.paysByStandingOrder(saver.getPersonalCode())).isFalse();
  }

  @Test
  void doesNotCountEmployerContributionsAsTheSaversOwnStandingOrder() {
    contribution(EMPLOYER_SOURCE, LocalDate.of(2026, 7, 10));
    contribution(EMPLOYER_SOURCE, LocalDate.of(2026, 8, 10));
    contribution(EMPLOYER_SOURCE, LocalDate.of(2026, 9, 10));

    assertThat(standingOrders.paysByStandingOrder(saver.getPersonalCode())).isFalse();
  }

  @Test
  void doesNotDetectAStandingOrderWhenEveryMonthWasPaidThroughOurPaymentLink() {
    linkPayment(SINGLE, Instant.parse("2026-07-03T10:00:00Z"));
    contribution(OWN_SOURCE, LocalDate.of(2026, 7, 6));
    linkPayment(SINGLE, Instant.parse("2026-08-04T10:00:00Z"));
    contribution(OWN_SOURCE, LocalDate.of(2026, 8, 5));
    linkPayment(SINGLE, Instant.parse("2026-09-04T10:00:00Z"));
    contribution(OWN_SOURCE, LocalDate.of(2026, 9, 7));
    linkPayment(SINGLE, Instant.parse("2026-10-05T10:00:00Z"));
    contribution(OWN_SOURCE, LocalDate.of(2026, 10, 6));

    assertThat(standingOrders.paysByStandingOrder(saver.getPersonalCode())).isFalse();
  }

  @Test
  void matchesALinkPaymentAtTheEndOfAMonthToTheContributionReportedTheNextMonth() {
    linkPayment(SINGLE, Instant.parse("2026-06-30T18:00:00Z"));
    contribution(OWN_SOURCE, LocalDate.of(2026, 7, 1));
    contribution(OWN_SOURCE, LocalDate.of(2026, 8, 10));
    contribution(OWN_SOURCE, LocalDate.of(2026, 9, 10));

    assertThat(standingOrders.paysByStandingOrder(saver.getPersonalCode())).isFalse();
  }

  @Test
  void doesNotCountAStandingOrderReportedWithinAWeekAfterALinkPaymentOfTheSameAmount() {
    contribution(OWN_SOURCE, LocalDate.of(2026, 7, 10));
    contribution(OWN_SOURCE, LocalDate.of(2026, 8, 10));
    linkPayment(SINGLE, Instant.parse("2026-09-07T10:00:00Z"));
    contribution(OWN_SOURCE, LocalDate.of(2026, 9, 8));
    contribution(OWN_SOURCE, LocalDate.of(2026, 9, 10));

    assertThat(standingOrders.paysByStandingOrder(saver.getPersonalCode())).isFalse();
  }

  @Test
  void doesNotCountAMonthWhoseContributionWasAGiftPaidThroughOurPaymentLink() {
    contribution(OWN_SOURCE, LocalDate.of(2026, 7, 10));
    contribution(OWN_SOURCE, LocalDate.of(2026, 8, 10));
    linkPayment(GIFT, Instant.parse("2026-09-08T10:00:00Z"));
    contribution(OWN_SOURCE, LocalDate.of(2026, 9, 9));

    assertThat(standingOrders.paysByStandingOrder(saver.getPersonalCode())).isFalse();
  }

  @Test
  void detectsAStandingOrderNextToALinkPaymentOfAnotherAmountInTheSameMonth() {
    contribution(OWN_SOURCE, LocalDate.of(2026, 7, 10));
    contribution(OWN_SOURCE, LocalDate.of(2026, 8, 10));
    linkPayment(SINGLE, Instant.parse("2026-09-08T10:00:00Z"), ONE_OFF_AMOUNT);
    contribution(OWN_SOURCE, LocalDate.of(2026, 9, 9), ONE_OFF_AMOUNT);
    contribution(OWN_SOURCE, LocalDate.of(2026, 9, 10));

    assertThat(standingOrders.paysByStandingOrder(saver.getPersonalCode())).isTrue();
  }

  @Test
  void detectsAStandingOrderReportedMoreThanAWeekAfterALinkPaymentOfTheSameAmount() {
    contribution(OWN_SOURCE, LocalDate.of(2026, 7, 10));
    contribution(OWN_SOURCE, LocalDate.of(2026, 8, 10));
    linkPayment(SINGLE, Instant.parse("2026-08-31T10:00:00Z"));
    contribution(OWN_SOURCE, LocalDate.of(2026, 9, 10));

    assertThat(standingOrders.paysByStandingOrder(saver.getPersonalCode())).isTrue();
  }

  private void contribution(String source, LocalDate reportedOn) {
    contribution(source, reportedOn, MONTHLY_AMOUNT);
  }

  private void contribution(String source, LocalDate reportedOn, BigDecimal amount) {
    entityManager.persist(
        exampleTransactionBuilder()
            .personalId(saver.getPersonalCode())
            .transactionSource(source)
            .reportingDate(reportedOn)
            .transactionValue(amount)
            .build());
  }

  private void linkPayment(PaymentType type, Instant paidAt) {
    linkPayment(type, paidAt, MONTHLY_AMOUNT);
  }

  private void linkPayment(PaymentType type, Instant paidAt, BigDecimal amount) {
    ClockHolder.setClock(Clock.fixed(paidAt, UTC));
    entityManager.persist(
        Payment.builder()
            .user(saver)
            .internalReference(UUID.randomUUID())
            .amount(amount)
            .recipientPersonalCode(saver.getPersonalCode())
            .paymentType(type)
            .build());
  }
}
