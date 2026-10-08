package ee.tuleva.onboarding.banking.payment;

import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckType.PAYMENT_BATCH_SETTLED;
import static ee.tuleva.onboarding.banking.payment.PaymentApprovalReminderJob.FROM_TWENTY_PAST_FOUR_CRON;
import static ee.tuleva.onboarding.banking.payment.PaymentApprovalReminderJob.UNTIL_SIX_CRON;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
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
      Instant.parse("2026-09-23T13:20:00Z");
  private static final Instant CHRISTMAS_EVE_AT_16_20_IN_TALLINN =
      Instant.parse("2026-12-24T14:20:00Z");

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
  void remind_postsTheReminderBesideTheBriefWhileBriefedPaymentsAreUnexecuted() {
    given(briefedBatches.on(WEDNESDAY)).willReturn(BATCH);
    given(reminder.forPaymentsStillUnexecuted(BATCH, WEDNESDAY))
        .willReturn(Optional.of("payments still unexecuted"));

    jobAt(WEDNESDAY_AT_16_20_IN_TALLINN).remind();

    verify(notificationService).sendMessage("payments still unexecuted", SAVINGS);
    verifyNoInteractions(settlementCheck);
  }

  @Test
  void remind_postsTheClosingOnceNothingAwaitsApprovalAndRemembersItWasPosted() {
    var closing =
        new PaymentSettlementCheck.Closing(PAYMENT_BATCH_SETTLED, "2026-09-23", "all fine");
    given(briefedBatches.on(WEDNESDAY)).willReturn(BATCH);
    given(reminder.forPaymentsStillUnexecuted(BATCH, WEDNESDAY)).willReturn(Optional.empty());
    given(settlementCheck.closingFor(BATCH, WEDNESDAY)).willReturn(Optional.of(closing));

    jobAt(WEDNESDAY_AT_16_20_IN_TALLINN).remind();

    verify(notificationService).sendMessage("all fine", SAVINGS);
    verify(settlementCheck).markPosted(closing);
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
  void reminders_fireEveryFiveMinutesFromTwentyPastFourUntilFiveToSix() {
    var fires = remindersOn("2026-09-23");

    assertThat(fires).hasSize(20);
    assertThat(fires.getFirst().toLocalTime()).hasToString("16:20");
    assertThat(fires.getLast().toLocalTime()).hasToString("17:55");
    for (int i = 1; i < fires.size(); i++) {
      assertThat(Duration.between(fires.get(i - 1), fires.get(i))).isEqualTo(Duration.ofMinutes(5));
    }
  }

  @Test
  void reminders_doNotFireAtTheWeekend() {
    assertThat(remindersOn("2026-09-26")).isEmpty();
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

  private static List<ZonedDateTime> remindersOn(String date) {
    var dayStart = LocalDateTime.parse(date + "T00:00:00").atZone(TALLINN);
    var dayEnd = dayStart.plusDays(1);
    return Stream.of(FROM_TWENTY_PAST_FOUR_CRON, UNTIL_SIX_CRON)
        .map(CronExpression::parse)
        .flatMap(
            cron ->
                Stream.iterate(cron.next(dayStart), fire -> fire != null, cron::next)
                    .takeWhile(dayEnd::isAfter))
        .sorted()
        .toList();
  }
}
