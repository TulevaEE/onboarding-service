package ee.tuleva.onboarding.savings.fund.reminder;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static java.time.temporal.ChronoUnit.DAYS;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
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
class PersonOnboardingAbandonmentReminderJob {

  private static final int REMINDER_DELAY_IN_DAYS = 3;
  private static final int OLDEST_VIEW_IN_DAYS = 30;
  private static final int MAX_RECIPIENTS = 200;

  private final Clock clock;
  private final PersonOnboardingAbandonmentReminderRepository repository;
  private final PersonOnboardingAbandonmentReminderSender sender;
  private final OperationsNotificationService notificationService;

  @Scheduled(cron = "0 45 12 * * *", zone = "Europe/Tallinn")
  @SchedulerLock(
      name = "PersonOnboardingAbandonmentReminderJob_sendReminders",
      lockAtMostFor = "23h",
      lockAtLeastFor = "30m")
  public void sendReminders() {
    var now = clock.instant();
    var viewedFrom = now.minus(OLDEST_VIEW_IN_DAYS, DAYS);
    var viewedUntil = now.minus(REMINDER_DELAY_IN_DAYS, DAYS);
    var reminders = repository.fetch(viewedFrom, viewedUntil);

    if (reminders.size() > MAX_RECIPIENTS) {
      log.error(
          "Too many person onboarding abandonment reminders, skipping: recipients={}, maxRecipients={}, viewedFrom={}, viewedUntil={}",
          reminders.size(),
          MAX_RECIPIENTS,
          viewedFrom,
          viewedUntil);
      notificationService.sendMessage(
          "Savings fund onboarding abandonment reminders skipped: %d people is over the %d cap, nobody will be reminded until someone looks"
              .formatted(reminders.size(), MAX_RECIPIENTS),
          SAVINGS,
          ERROR);
      return;
    }

    log.info("Sending person onboarding abandonment reminders: recipients={}", reminders.size());
    reminders.forEach(this::remind);
  }

  private void remind(PersonOnboardingAbandonmentReminder reminder) {
    try {
      sender.send(reminder);
    } catch (Exception e) {
      log.error(
          "Failed to send a person onboarding abandonment reminder: userId={}",
          reminder.userId(),
          e);
    }
  }
}
