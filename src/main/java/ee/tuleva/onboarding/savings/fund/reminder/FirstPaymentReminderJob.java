package ee.tuleva.onboarding.savings.fund.reminder;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static java.time.temporal.ChronoUnit.DAYS;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
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
  private static final int MAX_RECIPIENTS = 50;
  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final Instant MANUAL_CAMPAIGN_CUTOFF =
      LocalDate.of(2026, 8, 12).atStartOfDay(TALLINN).toInstant();
  private static final Instant MANUAL_COMPANY_FOLLOW_UP_CUTOFF =
      LocalDate.of(2026, 10, 27).atStartOfDay(TALLINN).toInstant();

  private final Clock clock;
  private final FirstPaymentReminderRepository repository;
  private final FirstPaymentReminderSender sender;
  private final OperationsNotificationService notificationService;

  @Scheduled(cron = "0 0 12 * * *", zone = "Europe/Tallinn")
  @SchedulerLock(
      name = "FirstPaymentReminderJob_sendReminders",
      lockAtMostFor = "23h",
      lockAtLeastFor = "30m")
  public void sendReminders() {
    var now = clock.instant();
    var windowStart = now.minus(OLDEST_ACCOUNT_IN_DAYS, DAYS);
    var openedFrom = notBefore(MANUAL_CAMPAIGN_CUTOFF, windowStart);
    var companiesOpenedFrom = notBefore(MANUAL_COMPANY_FOLLOW_UP_CUTOFF, windowStart);
    var openedUntil = now.minus(REMINDER_DELAY_IN_DAYS, DAYS);

    remindSegment(
        "adults", repository.fetchForAdults(openedFrom, openedUntil), openedFrom, openedUntil);
    remindSegment(
        "children", repository.fetchForChildren(openedFrom, openedUntil), openedFrom, openedUntil);
    remindSegment(
        "companies",
        repository.fetchForCompanies(companiesOpenedFrom, openedUntil),
        companiesOpenedFrom,
        openedUntil);
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
      notificationService.sendMessage(
          "Savings fund first payment reminders skipped for %s: %d recipients is over the %d cap, nobody in that segment will be reminded until someone looks"
              .formatted(segment, reminders.size(), MAX_RECIPIENTS),
          SAVINGS,
          ERROR);
      return;
    }

    log.info(
        "Sending savings fund first payment reminders: segment={}, recipients={}",
        segment,
        reminders.size());
    reminders.forEach(this::remind);
  }

  private static Instant notBefore(Instant cutoff, Instant windowStart) {
    return windowStart.isAfter(cutoff) ? windowStart : cutoff;
  }

  private void remind(FirstPaymentReminder reminder) {
    try {
      sender.send(reminder);
    } catch (Exception e) {
      log.error("Failed to send a savings fund first payment reminder", e);
    }
  }
}
