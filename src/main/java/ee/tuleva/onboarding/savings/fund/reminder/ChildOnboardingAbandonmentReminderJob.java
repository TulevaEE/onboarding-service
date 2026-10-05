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
class ChildOnboardingAbandonmentReminderJob {

  private static final int REMINDER_DELAY_IN_DAYS = 3;
  private static final int OLDEST_START_IN_DAYS = 30;
  private static final int MAX_RECIPIENTS = 100;

  private final Clock clock;
  private final ChildOnboardingAbandonmentReminderRepository repository;
  private final ChildOnboardingAbandonmentReminderSender sender;
  private final OperationsNotificationService notificationService;

  @Scheduled(cron = "0 30 12 * * *", zone = "Europe/Tallinn")
  @SchedulerLock(
      name = "ChildOnboardingAbandonmentReminderJob_sendReminders",
      lockAtMostFor = "23h",
      lockAtLeastFor = "30m")
  public void sendReminders() {
    var now = clock.instant();
    var startedFrom = now.minus(OLDEST_START_IN_DAYS, DAYS);
    var startedUntil = now.minus(REMINDER_DELAY_IN_DAYS, DAYS);
    var reminders = repository.fetch(startedFrom, startedUntil);

    if (reminders.size() > MAX_RECIPIENTS) {
      log.error(
          "Too many child onboarding abandonment reminders, skipping: recipients={}, maxRecipients={}, startedFrom={}, startedUntil={}",
          reminders.size(),
          MAX_RECIPIENTS,
          startedFrom,
          startedUntil);
      notificationService.sendMessage(
          "Child savings fund onboarding abandonment reminders skipped: %d parents is over the %d cap, nobody will be reminded until someone looks"
              .formatted(reminders.size(), MAX_RECIPIENTS),
          SAVINGS,
          ERROR);
      return;
    }

    log.info("Sending child onboarding abandonment reminders: recipients={}", reminders.size());
    reminders.forEach(this::remind);
  }

  private void remind(ChildOnboardingAbandonmentReminder reminder) {
    try {
      sender.send(reminder);
    } catch (Exception e) {
      log.error("Failed to send a child onboarding abandonment reminder", e);
    }
  }
}
