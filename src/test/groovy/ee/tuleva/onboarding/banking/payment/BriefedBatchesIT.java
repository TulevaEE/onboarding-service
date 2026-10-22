package ee.tuleva.onboarding.banking.payment;

import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.EXECUTED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.SUBMITTED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentType.RETURN;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.banking.check.payment.PaymentCheckService;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.time.ClockConfig;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest
@Import({BriefedBatches.class, PaymentCheckService.class, ClockConfig.class, PublicHolidays.class})
class BriefedBatchesIT {

  private static final LocalDate MONDAY = LocalDate.of(2026, 9, 28);
  private static final Instant FRIDAYS_BRIEF = Instant.parse("2026-09-25T13:10:00Z");
  private static final Instant MONDAYS_BRIEF = Instant.parse("2026-09-28T13:10:00Z");

  private static final LocalDate MONDAY_AFTER_CHRISTMAS = LocalDate.of(2026, 12, 28);
  private static final Instant BRIEF_BEFORE_CHRISTMAS = Instant.parse("2026-12-23T14:10:00Z");
  private static final Instant BRIEF_AFTER_CHRISTMAS = Instant.parse("2026-12-28T14:10:00Z");

  @Autowired BriefedBatches briefedBatches;
  @Autowired OutgoingPaymentRepository outgoingPaymentRepository;
  @MockitoBean OperationsNotificationService notificationService;

  @Test
  void onAMonday_followsWhatWasSentSinceFridaysBriefAndCarriesOverWhatFridaysBriefListed() {
    var listedOnFriday = store(SUBMITTED, Instant.parse("2026-09-25T13:09:00Z"));
    var sentAfterFridaysBrief = store(EXECUTED, Instant.parse("2026-09-25T13:11:00Z"));
    var sentOnSunday = store(SUBMITTED, Instant.parse("2026-09-27T01:02:00Z"));
    store(SUBMITTED, Instant.parse("2026-09-28T13:11:00Z"));

    assertThat(briefedBatches.on(MONDAY))
        .usingRecursiveComparison()
        .ignoringCollectionOrder()
        .isEqualTo(
            new BriefedBatch(
                FRIDAYS_BRIEF,
                MONDAYS_BRIEF,
                List.of(sentAfterFridaysBrief, sentOnSunday),
                List.of(listedOnFriday)));
  }

  @Test
  void afterPublicHolidays_followsWhatWasSentSinceTheLastWorkingDaysBrief() {
    var listedBeforeChristmas = store(SUBMITTED, Instant.parse("2026-12-23T14:09:00Z"));
    var sentAfterTheBriefBeforeChristmas = store(EXECUTED, Instant.parse("2026-12-23T14:11:00Z"));
    var sentOnChristmasEve = store(SUBMITTED, Instant.parse("2026-12-24T10:00:00Z"));
    store(SUBMITTED, Instant.parse("2026-12-28T14:11:00Z"));

    assertThat(briefedBatches.on(MONDAY_AFTER_CHRISTMAS))
        .usingRecursiveComparison()
        .ignoringCollectionOrder()
        .isEqualTo(
            new BriefedBatch(
                BRIEF_BEFORE_CHRISTMAS,
                BRIEF_AFTER_CHRISTMAS,
                List.of(sentAfterTheBriefBeforeChristmas, sentOnChristmasEve),
                List.of(listedBeforeChristmas)));
  }

  @Test
  void listsPaymentsInTheOrderTheyWereSentSoTheSameProblemsAlwaysReadTheSameAndAreReportedOnce() {
    var listedOnFridayLater = store(SUBMITTED, Instant.parse("2026-09-25T12:00:00Z"));
    var listedOnFridayEarlier = store(SUBMITTED, Instant.parse("2026-09-25T09:00:00Z"));
    var sentOnMonday = store(SUBMITTED, Instant.parse("2026-09-28T09:00:00Z"));
    var sentOnSunday = store(SUBMITTED, Instant.parse("2026-09-27T09:00:00Z"));

    var batch = briefedBatches.on(MONDAY);

    assertThat(batch.payments()).containsExactly(sentOnSunday, sentOnMonday);
    assertThat(batch.carriedOver()).containsExactly(listedOnFridayEarlier, listedOnFridayLater);
  }

  @Test
  void carriesOverAPaymentFromAnEarlierBriefThatTheBankExecutedOnlyAfterTodaysBrief() {
    var executedAfterMondaysBrief =
        store(
            EXECUTED, Instant.parse("2026-09-25T09:00:00Z"), Instant.parse("2026-09-28T13:40:00Z"));
    store(EXECUTED, Instant.parse("2026-09-25T09:00:00Z"), Instant.parse("2026-09-28T09:00:00Z"));

    assertThat(briefedBatches.on(MONDAY).carriedOver()).containsExactly(executedAfterMondaysBrief);
  }

  private OutgoingPayment store(OutgoingPaymentStatus status, Instant attemptedAt) {
    return store(status, attemptedAt, attemptedAt);
  }

  private OutgoingPayment store(
      OutgoingPaymentStatus status, Instant attemptedAt, Instant resolvedAt) {
    return outgoingPaymentRepository.save(
        OutgoingPayment.builder()
            .endToEndId(randomUUID().toString().replace("-", ""))
            .paymentType(RETURN)
            .sourceId(randomUUID())
            .remitterIban("EE444444444444444444")
            .beneficiaryIban("EE777777777777777777")
            .amount(new BigDecimal("50.00"))
            .currency("EUR")
            .bodyHash("hash")
            .status(status)
            .attemptedAt(attemptedAt)
            .resolvedAt(resolvedAt)
            .build());
  }
}
