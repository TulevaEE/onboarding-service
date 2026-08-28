package ee.tuleva.onboarding.savings.fund.reminder;

import static java.time.temporal.ChronoUnit.DAYS;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
@Profile("!dev")
class FirstPaymentReminderJob {

  private static final int REMINDER_DELAY_IN_DAYS = 7;
  private static final int OLDEST_ACCOUNT_IN_DAYS = 30;
  private static final int MAX_RECIPIENTS = 200;
  private static final Instant MANUAL_CAMPAIGN_CUTOFF =
      LocalDate.of(2026, 8, 12).atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant();

  private final Clock clock;
  private final FirstPaymentReminderRepository repository;
  private final FirstPaymentReminderSender sender;

  @Scheduled(cron = "0 0 12 * * *", zone = "Europe/Tallinn")
  @SchedulerLock(
      name = "FirstPaymentReminderJob_sendReminders",
      lockAtMostFor = "23h",
      lockAtLeastFor = "30m")
  public void sendReminders() {
    var now = clock.instant();
    var openedFrom = notBeforeTheManualCampaign(now.minus(OLDEST_ACCOUNT_IN_DAYS, DAYS));
    var openedUntil = now.minus(REMINDER_DELAY_IN_DAYS, DAYS);

    remindSegment(
        "adults", repository.fetchForAdults(openedFrom, openedUntil), openedFrom, openedUntil);
    remindSegment(
        "children", repository.fetchForChildren(openedFrom, openedUntil), openedFrom, openedUntil);
  }

  private void remindSegment(
      String segment,
      List<FirstPaymentReminder> reminders,
      Instant openedFrom,
      Instant openedUntil) {
    if (reminders.size() > MAX_RECIPIENTS) {
      log.error(
          "Too many savings fund first payment reminders, skipping segment: segment={}, recipients={}, maxRecipients={}, openedFrom={}, openedUntil={}",
          segment,
          reminders.size(),
          MAX_RECIPIENTS,
          openedFrom,
          openedUntil);
      return;
    }

    log.info(
        "Sending savings fund first payment reminders: segment={}, recipients={}",
        segment,
        reminders.size());
    reminders.forEach(this::remind);
  }

  private Instant notBeforeTheManualCampaign(Instant windowStart) {
    return windowStart.isAfter(MANUAL_CAMPAIGN_CUTOFF) ? windowStart : MANUAL_CAMPAIGN_CUTOFF;
  }

  private void remind(FirstPaymentReminder reminder) {
    try {
      sender.send(reminder);
    } catch (Exception e) {
      log.error("Failed to send a savings fund first payment reminder", e);
    }
  }
}
