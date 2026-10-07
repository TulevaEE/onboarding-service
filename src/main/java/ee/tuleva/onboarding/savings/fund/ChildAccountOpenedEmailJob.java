package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static java.time.temporal.ChronoUnit.DAYS;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
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
class ChildAccountOpenedEmailJob {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final int OLDEST_OPENING_IN_DAYS = 3;
  private static final int MAX_RECIPIENTS = 100;

  private final Clock clock;
  private final OpenedChildAccountRepository repository;
  private final ChildAccountOpenedEmailSender sender;
  private final OperationsNotificationService notificationService;

  @Scheduled(cron = "0 0 9 * * *", zone = "Europe/Tallinn")
  @SchedulerLock(
      name = "ChildAccountOpenedEmailJob_sendEmails",
      lockAtMostFor = "23h",
      lockAtLeastFor = "30m")
  public void sendEmails() {
    var openedUntil = LocalDate.now(clock.withZone(TALLINN)).atStartOfDay(TALLINN).toInstant();
    var openedFrom = openedUntil.minus(OLDEST_OPENING_IN_DAYS, DAYS);
    var accounts = repository.fetch(openedFrom, openedUntil);

    if (accounts.size() > MAX_RECIPIENTS) {
      log.error(
          "Too many child account opened emails, skipping: accounts={}, maxRecipients={}, openedFrom={}, openedUntil={}",
          accounts.size(),
          MAX_RECIPIENTS,
          openedFrom,
          openedUntil);
      notificationService.sendMessage(
          "Child savings fund account opened emails skipped: %d accounts is over the %d cap, nobody will be welcomed until someone looks"
              .formatted(accounts.size(), MAX_RECIPIENTS),
          SAVINGS,
          ERROR);
      return;
    }

    log.info("Sending child account opened emails: accounts={}", accounts.size());
    accounts.forEach(this::welcome);
  }

  private void welcome(OpenedChildAccount account) {
    try {
      sender.send(account);
    } catch (Exception e) {
      log.error("Failed to send a child account opened email", e);
    }
  }
}
