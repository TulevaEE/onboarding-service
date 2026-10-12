package ee.tuleva.onboarding.banking.payment;

import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckType.PAYMENT_BATCH_SETTLED;
import static ee.tuleva.onboarding.banking.payment.PaymentApprovalReminderJob.EVERY_FIVE_MINUTES_UNTIL_QUARTER_TO_FIVE_CRON;
import static ee.tuleva.onboarding.banking.payment.PaymentApprovalReminderJob.EVERY_QUARTER_HOUR_AFTER_QUARTER_TO_FIVE_CRON;
import static ee.tuleva.onboarding.banking.payment.PaymentApprovalReminderJob.FROM_QUARTER_PAST_FOUR_CRON;
import static ee.tuleva.onboarding.banking.payment.PaymentApprovalReminderJob.UNTIL_SIX_CRON;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.support.CronExpression;

@ExtendWith(MockitoExtension.class)
class PaymentApprovalReminderJobTest {
  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final LocalDate WEDNESDAY = LocalDate.parse("2026-09-23");
  private static final Instant WEDNESDAY_AT_16_20_IN_TALLINN =
      Instant.parse("2026-09-23T13:20:00.040Z");
  private static final Instant WEDNESDAY_AT_17_05_IN_TALLINN =
      Instant.parse("2026-09-23T14:05:00.040Z");
  private static final Instant WEDNESDAY_AT_17_55_IN_TALLINN =
      Instant.parse("2026-09-23T14:55:00.040Z");
  private static final Instant CHRISTMAS_EVE_AT_16_20_IN_TALLINN =
      Instant.parse("2026-12-24T14:20:00Z");
  private static final PaymentSettlementCheck.Closing CLOSING =
      new PaymentSettlementCheck.Closing(
          PAYMENT_BATCH_SETTLED, "2026-09-23", "all fine", List.of());

  private static final BriefedBatch BATCH =
      new BriefedBatch(
          Instant.parse("2026-09-22T13:10:00Z"),
          Instant.parse("2026-09-23T13:10:00Z"),
          List.of(),
          List.of());

  @Mock private BriefedBatches briefedBatches;
  @Mock private PaymentApprovalReminder reminder;
  @Mock private PaymentSettlementCheck settlementCheck;
  @Mock private OperationsNotificationService notificationService;

  @Test
  void remind_postsTheReminderOnAReminderRunWhileBriefedPaymentsAreUnexecuted() {
    given(briefedBatches.on(WEDNESDAY)).willReturn(BATCH);
    given(reminder.forPaymentsStillUnexecuted(BATCH, WEDNESDAY))
        .willReturn(Optional.of("payments still unexecuted"));
    given(settlementCheck.closingFor(BATCH, WEDNESDAY)).willReturn(Optional.empty());

    jobAt(WEDNESDAY_AT_16_20_IN_TALLINN).remind();

    then(notificationService).should().sendMessage("payments still unexecuted", SAVINGS);
    then(notificationService).shouldHaveNoMoreInteractions();
  }

  @Test
  void remind_leavesOutTheReminderOnTheRunsBetweenTheQuarterHoursAfterQuarterToFive() {
    given(briefedBatches.on(WEDNESDAY)).willReturn(BATCH);
    given(settlementCheck.closingFor(BATCH, WEDNESDAY)).willReturn(Optional.empty());

    jobAt(WEDNESDAY_AT_17_05_IN_TALLINN).remind();

    verifyNoInteractions(reminder, notificationService);
  }

  @Test
  void remind_postsTheClosingOnAnyRunAndRemembersItWasPosted() {
    given(briefedBatches.on(WEDNESDAY)).willReturn(BATCH);
    given(settlementCheck.closingFor(BATCH, WEDNESDAY)).willReturn(Optional.of(CLOSING));

    jobAt(WEDNESDAY_AT_17_05_IN_TALLINN).remind();

    then(notificationService).should().sendMessage("all fine", SAVINGS);
    then(settlementCheck).should().markPosted(CLOSING);
  }

  @Test
  void remind_closesTheDayWithTheLastClosingOnTheLastRun() {
    given(briefedBatches.on(WEDNESDAY)).willReturn(BATCH);
    given(settlementCheck.lastClosingOfTheDayFor(BATCH, WEDNESDAY))
        .willReturn(Optional.of(CLOSING));

    jobAt(WEDNESDAY_AT_17_55_IN_TALLINN).remind();

    then(notificationService).should().sendMessage("all fine", SAVINGS);
    then(settlementCheck).should().markPosted(CLOSING);
    then(settlementCheck).shouldHaveNoMoreInteractions();
  }

  @Test
  void remind_staysSilentOnceTheClosingHasBeenPosted() {
    given(briefedBatches.on(WEDNESDAY)).willReturn(BATCH);
    given(reminder.forPaymentsStillUnexecuted(BATCH, WEDNESDAY)).willReturn(Optional.empty());
    given(settlementCheck.closingFor(BATCH, WEDNESDAY)).willReturn(Optional.empty());

    jobAt(WEDNESDAY_AT_16_20_IN_TALLINN).remind();

    verifyNoInteractions(notificationService);
  }

  @Test
  void remind_staysSilentOnAPublicHolidayBecauseNoBriefWasPosted() {
    jobAt(CHRISTMAS_EVE_AT_16_20_IN_TALLINN).remind();

    verifyNoInteractions(briefedBatches, reminder, settlementCheck, notificationService);
  }

  @Test
  void runs_fireEveryFiveMinutesFromQuarterPastFourUntilFiveToSix() {
    var fires = firesOn("2026-09-23", FROM_QUARTER_PAST_FOUR_CRON, UNTIL_SIX_CRON);

    assertThat(fires).hasSize(21);
    assertThat(fires.getFirst().toLocalTime()).hasToString("16:15");
    assertThat(fires.getLast().toLocalTime()).hasToString("17:55");
    for (int i = 1; i < fires.size(); i++) {
      assertThat(Duration.between(fires.get(i - 1), fires.get(i))).isEqualTo(Duration.ofMinutes(5));
    }
  }

  @Test
  void reminders_comeEveryFiveMinutesUntilQuarterToFiveThenEveryQuarterHourOnRunsOfTheJob() {
    var reminders =
        firesOn(
            "2026-09-23",
            EVERY_FIVE_MINUTES_UNTIL_QUARTER_TO_FIVE_CRON,
            EVERY_QUARTER_HOUR_AFTER_QUARTER_TO_FIVE_CRON);

    assertThat(reminders.stream().map(fire -> fire.toLocalTime().toString()))
        .containsExactly(
            "16:15", "16:20", "16:25", "16:30", "16:35", "16:40", "16:45", "17:00", "17:15",
            "17:30", "17:45");
    assertThat(firesOn("2026-09-23", FROM_QUARTER_PAST_FOUR_CRON, UNTIL_SIX_CRON))
        .containsAll(reminders);
  }

  @Test
  void runs_doNotFireAtTheWeekend() {
    assertThat(firesOn("2026-09-26", FROM_QUARTER_PAST_FOUR_CRON, UNTIL_SIX_CRON)).isEmpty();
  }

  private PaymentApprovalReminderJob jobAt(Instant now) {
    return new PaymentApprovalReminderJob(
        briefedBatches,
        reminder,
        settlementCheck,
        notificationService,
        new PublicHolidays(),
        Clock.fixed(now, UTC));
  }

  private static List<ZonedDateTime> firesOn(String date, String... crons) {
    var dayStart = LocalDateTime.parse(date + "T00:00:00").atZone(TALLINN);
    var dayEnd = dayStart.plusDays(1);
    return Stream.of(crons)
        .map(CronExpression::parse)
        .flatMap(
            cron ->
                Stream.iterate(cron.next(dayStart), fire -> fire != null, cron::next)
                    .takeWhile(dayEnd::isAfter))
        .sorted()
        .toList();
  }
}
